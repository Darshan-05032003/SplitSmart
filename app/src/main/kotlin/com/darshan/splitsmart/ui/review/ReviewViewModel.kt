package com.darshan.splitsmart.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.data.ocr.ReceiptImageStore
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.ExtractionResult
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.NameValidation
import com.darshan.splitsmart.domain.model.NameValidator
import com.darshan.splitsmart.domain.model.ReceiptDraft
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.repository.ReceiptDraftRepository
import com.darshan.splitsmart.domain.usecase.ExtractReceiptUseCase
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.toErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

enum class ReviewPhase { LOADING, EXTRACTING, EDITING, SAVING, ERROR }
enum class ItemError { NAME, QTY, AMOUNT }

@Serializable
data class ItemRow(val key: String, val name: String, val qty: String, val amount: String)

/** Everything the user can edit; serialised into SavedStateHandle so edits survive process death. */
@Serializable
data class ReviewForm(
    val title: String = "",
    val items: List<ItemRow> = emptyList(),
    val tax: String = "",
    val tip: String = "",
    val total: String = "",
    val payerId: String? = null,
    val source: ExtractionSource = ExtractionSource.REGEX,
    val llmFailure: LlmFailure? = null,
    val warnings: Set<ExtractionWarning> = emptySet(),
    val retryAfterSeconds: Long? = null,
)

data class ReviewErrors(
    val title: NameError? = null,
    val items: Map<String, Set<ItemError>> = emptyMap(),
    val tax: Boolean = false,
    val tip: Boolean = false,
    val total: Boolean = false,
) {
    val any: Boolean get() = title != null || items.isNotEmpty() || tax || tip || total
}

data class ReviewUiState(
    val phase: ReviewPhase = ReviewPhase.LOADING,
    val form: ReviewForm? = null,
    val errors: ReviewErrors = ReviewErrors(),
    val members: List<Member> = emptyList(),
    val subtotalPaise: Long? = null,
    val mismatchPaise: Long? = null,
    val error: ErrorReason? = null,
    /** The form shows the on-device parser's provisional draft while the AI call is still running. */
    val refining: Boolean = false,
    /** The AI answered after the user had already edited the provisional draft: offered, never forced. */
    val pendingAi: ReviewForm? = null,
)

sealed interface ReviewEvent {
    data class Saved(val billId: String) : ReviewEvent
    data object Failed : ReviewEvent
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val drafts: ReceiptDraftRepository,
    private val extractReceipt: ExtractReceiptUseCase,
    private val billRepository: BillRepository,
    private val groupRepository: GroupRepository,
    private val imageStore: ReceiptImageStore,
    private val clock: Clock,
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : ViewModel() {
    val draftId: String = checkNotNull(savedStateHandle[ARG_DRAFT_ID]) { "draftId missing" }
    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()
    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events: Flow<ReviewEvent> = _events.receiveAsFlow()
    private var draft: ReceiptDraft? = null
    private var extractionJob: Job? = null
    private var editedWhileRefining = false

    init {
        load()
    }

    private fun load() {
        _uiState.value = ReviewUiState(phase = ReviewPhase.LOADING)
        extractionJob = viewModelScope.launch {
            val loaded = runCatching { drafts.get(draftId) }.getOrElse { fail(it.toErrorReason()); return@launch }
                ?: run { fail(ErrorReason.NOT_FOUND); return@launch }
            draft = loaded
            val members = runCatching { groupRepository.getMembers(loaded.groupId) }.getOrDefault(emptyList())
            val restored = savedStateHandle.get<String>(KEY_FORM)?.let { runCatching { json.decodeFromString<ReviewForm>(it) }.getOrNull() }
            if (restored != null) {
                _uiState.value = ReviewUiState(phase = ReviewPhase.EDITING, members = members)
                setForm(restored, persist = false, phase = ReviewPhase.EDITING)
            } else {
                _uiState.value = ReviewUiState(phase = ReviewPhase.EXTRACTING, members = members)
                runExtraction(loaded)
            }
        }
    }

    /**
     * Runs the LLM extraction. If it has not answered within [PROVISIONAL_DELAY_MS], the on-device parser's result is
     * shown as an editable provisional draft ("AI is refining…") so the user is never blocked on the network. When the
     * AI answers it replaces the draft, unless the user already edited it: then the AI result is offered instead.
     */
    private suspend fun runExtraction(draft: ReceiptDraft) = coroutineScope {
        editedWhileRefining = false
        val provisional = launch {
            delay(PROVISIONAL_DELAY_MS)
            val result = extractReceipt.provisional(draft.ocrText)
            if (_uiState.value.phase == ReviewPhase.EXTRACTING) {
                setForm(result.toForm(payerId = _uiState.value.form?.payerId, title = _uiState.value.form?.title ?: ""), persist = false, phase = ReviewPhase.EDITING, refining = true)
            }
        }
        val result = extractReceipt.extract(draft.ocrText)
        provisional.cancel()
        val state = _uiState.value
        val current = state.form
        val aiForm = result.toForm(payerId = current?.payerId, title = current?.title ?: "")
        when {
            state.phase != ReviewPhase.EXTRACTING && state.phase != ReviewPhase.EDITING -> Unit // saving / left
            state.refining && editedWhileRefining && current != null -> {
                if (result.source == ExtractionSource.LLM) {
                    _uiState.update { it.copy(refining = false, pendingAi = aiForm) }
                } else {
                    setForm(current.copy(llmFailure = result.llmFailure, retryAfterSeconds = result.retryAfterSeconds), refining = false)
                }
            }
            else -> setForm(aiForm, persist = true, phase = ReviewPhase.EDITING, refining = false)
        }
    }

    /** Replaces the edited draft with the AI result the user was offered (title and payer are kept). */
    fun applyAiResult() {
        val pending = _uiState.value.pendingAi ?: return
        val current = _uiState.value.form ?: return
        if (_uiState.value.phase != ReviewPhase.EDITING) return
        setForm(pending.copy(title = current.title, payerId = current.payerId), phase = ReviewPhase.EDITING, clearPendingAi = true)
    }

    fun dismissAiResult() = _uiState.update { it.copy(pendingAi = null) }

    private fun fail(reason: ErrorReason) {
        _uiState.update { it.copy(phase = ReviewPhase.ERROR, error = reason) }
    }

    fun retry() {
        if (_uiState.value.phase == ReviewPhase.ERROR) load()
    }

    /** Runs the extraction again (e.g. after a transient LLM failure); the current edits are replaced. */
    fun rerunExtraction() {
        val current = draft ?: return
        if (_uiState.value.phase != ReviewPhase.EDITING) return
        _uiState.update { it.copy(phase = ReviewPhase.EXTRACTING, pendingAi = null) }
        extractionJob = viewModelScope.launch { runExtraction(current) }
    }

    // ----- editing -----
    private fun setForm(form: ReviewForm, persist: Boolean = true, phase: ReviewPhase? = null, refining: Boolean? = null, clearPendingAi: Boolean = false) {
        val subtotal = form.items.sumOf { Money.parseInrToPaise(it.amount) ?: 0L }
        val total = Money.parseInrToPaise(form.total)
        val mismatch = total?.let { subtotal + (Money.parseInrToPaise(form.tax) ?: 0L) + (Money.parseInrToPaise(form.tip) ?: 0L) - it }
        _uiState.update {
            it.copy(
                phase = phase ?: it.phase, form = form, subtotalPaise = subtotal, mismatchPaise = mismatch,
                errors = if (phase != null) ReviewErrors() else it.errors, refining = refining ?: it.refining,
                pendingAi = if (clearPendingAi) null else it.pendingAi,
            )
        }
        if (persist) savedStateHandle[KEY_FORM] = json.encodeToString(ReviewForm.serializer(), form)
    }

    private fun updateForm(marksEdit: Boolean = true, transform: (ReviewForm) -> ReviewForm) {
        val current = _uiState.value.form ?: return
        if (_uiState.value.phase != ReviewPhase.EDITING) return
        if (marksEdit && _uiState.value.refining) editedWhileRefining = true
        setForm(transform(current))
    }

    private fun clearItemError(key: String, error: ItemError) = _uiState.update { s ->
        val errors = s.errors.items[key]?.minus(error).orEmpty()
        s.copy(errors = s.errors.copy(items = if (errors.isEmpty()) s.errors.items - key else s.errors.items + (key to errors)))
    }

    fun onTitleChange(v: String) { updateForm(marksEdit = false) { it.copy(title = v.take(INPUT_LIMIT)) }; _uiState.update { it.copy(errors = it.errors.copy(title = null)) } }
    fun onPayerChange(memberId: String?) = updateForm(marksEdit = false) { it.copy(payerId = memberId) }
    fun onItemNameChange(key: String, v: String) { updateItem(key) { it.copy(name = v.take(INPUT_LIMIT)) }; clearItemError(key, ItemError.NAME) }
    fun onItemQtyChange(key: String, v: String) { updateItem(key) { it.copy(qty = v.take(4)) }; clearItemError(key, ItemError.QTY) }
    fun onItemAmountChange(key: String, v: String) { updateItem(key) { it.copy(amount = v.take(AMOUNT_LIMIT)) }; clearItemError(key, ItemError.AMOUNT) }
    fun onTaxChange(v: String) { updateForm { it.copy(tax = v.take(AMOUNT_LIMIT)) }; _uiState.update { it.copy(errors = it.errors.copy(tax = false)) } }
    fun onTipChange(v: String) { updateForm { it.copy(tip = v.take(AMOUNT_LIMIT)) }; _uiState.update { it.copy(errors = it.errors.copy(tip = false)) } }
    fun onTotalChange(v: String) { updateForm { it.copy(total = v.take(AMOUNT_LIMIT)) }; _uiState.update { it.copy(errors = it.errors.copy(total = false)) } }

    private fun updateItem(key: String, transform: (ItemRow) -> ItemRow) = updateForm { form ->
        form.copy(items = form.items.map { if (it.key == key) transform(it) else it })
    }

    fun addItem() = updateForm { form ->
        if (form.items.size >= Bill.MAX_ITEMS) form else form.copy(items = form.items + ItemRow(UUID.randomUUID().toString(), "", "1", ""))
    }

    fun removeItem(key: String) {
        updateForm { form -> form.copy(items = form.items.filterNot { it.key == key }) }
        _uiState.update { it.copy(errors = it.errors.copy(items = it.errors.items - key)) }
    }

    /** Replaces the total with items + tax + tip. */
    fun useComputedTotal() = updateForm { form ->
        val subtotal = form.items.sumOf { Money.parseInrToPaise(it.amount) ?: 0L }
        form.copy(total = Money.toPlainRupees(subtotal + (Money.parseInrToPaise(form.tax) ?: 0L) + (Money.parseInrToPaise(form.tip) ?: 0L)))
    }

    // ----- saving -----
    fun save(defaultTitle: String) {
        val state = _uiState.value
        val form = state.form ?: return
        val current = draft ?: return
        if (state.phase != ReviewPhase.EDITING) return
        val rows = form.items.filterNot { it.name.isBlank() && it.amount.isBlank() }
        val errors = validate(form.copy(items = rows), defaultTitle)
        if (errors.any) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        if (state.refining) extractionJob?.cancel() // the user is happy with the draft: stop waiting for the AI
        _uiState.update { it.copy(phase = ReviewPhase.SAVING, errors = ReviewErrors(), refining = false, pendingAi = null) }
        viewModelScope.launch {
            try {
                val bill = buildBill(current, form.copy(items = rows), defaultTitle)
                billRepository.saveBill(bill)
                runCatching { drafts.delete(current.id) }
                current.imagePath?.let { path -> imageStore.resolveOwnedFile(File(path).name)?.let { runCatching { imageStore.delete(it) } } }
                savedStateHandle[KEY_FORM] = null
                _events.send(ReviewEvent.Saved(bill.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = ReviewPhase.EDITING) }
                _events.send(ReviewEvent.Failed)
            }
        }
    }

    private fun validate(form: ReviewForm, defaultTitle: String): ReviewErrors {
        val titleError = (NameValidator.validate(form.title.ifBlank { defaultTitle }, Bill.MAX_TITLE_LENGTH) as? NameValidation.Invalid)?.error
        val itemErrors = form.items.mapNotNull { row ->
            val errors = buildSet {
                if (NameValidator.validate(row.name) is NameValidation.Invalid) add(ItemError.NAME)
                if (row.qty.trim().toIntOrNull()?.takeIf { it in 1..BillItem.MAX_QTY } == null) add(ItemError.QTY)
                if (Money.parseInrToPaise(row.amount) == null) add(ItemError.AMOUNT)
            }
            if (errors.isEmpty()) null else row.key to errors
        }.toMap()
        return ReviewErrors(
            title = titleError,
            items = itemErrors,
            tax = form.tax.isNotBlank() && Money.parseInrToPaise(form.tax) == null,
            tip = form.tip.isNotBlank() && Money.parseInrToPaise(form.tip) == null,
            total = (Money.parseInrToPaise(form.total) ?: 0L) <= 0L,
        )
    }

    private fun buildBill(draft: ReceiptDraft, form: ReviewForm, defaultTitle: String): Bill {
        val billId = UUID.randomUUID().toString()
        val title = (NameValidator.validate(form.title.ifBlank { defaultTitle }, Bill.MAX_TITLE_LENGTH) as NameValidation.Valid).name
        val items = form.items.mapIndexed { index, row ->
            val qty = row.qty.trim().toInt()
            val total = Money.parseInrToPaise(row.amount)!!
            BillItem(
                id = UUID.randomUUID().toString(), billId = billId, name = (NameValidator.validate(row.name) as NameValidation.Valid).name,
                qty = qty, unitPaise = if (total % qty == 0L) total / qty else 0L, totalPaise = total, sortOrder = index,
            )
        }
        return Bill(
            id = billId, groupId = draft.groupId, title = title, totalPaise = Money.parseInrToPaise(form.total)!!,
            taxPaise = Money.parseInrToPaise(form.tax) ?: 0L, tipPaise = Money.parseInrToPaise(form.tip) ?: 0L,
            paidByMemberId = form.payerId?.takeIf { id -> _uiState.value.members.any { it.id == id } },
            source = if (form.source == ExtractionSource.LLM) BillSource.SCAN_LLM else BillSource.SCAN_REGEX,
            status = BillStatus.DRAFT, createdAt = clock.instant(), items = items,
        )
    }

    private fun ExtractionResult.toForm(payerId: String?, title: String) = ReviewForm(
        title = title,
        items = receipt.items.map { ItemRow(UUID.randomUUID().toString(), it.name, it.qty.toString(), Money.toPlainRupees(it.totalPaise)) },
        tax = Money.toPlainRupees(receipt.taxPaise),
        tip = Money.toPlainRupees(receipt.tipPaise),
        total = Money.toPlainRupees(receipt.totalPaise),
        payerId = payerId,
        source = source,
        llmFailure = llmFailure,
        warnings = warnings,
        retryAfterSeconds = retryAfterSeconds,
    )

    companion object {
        const val ARG_DRAFT_ID = "draftId"
        private const val KEY_FORM = "form"
        private const val INPUT_LIMIT = 200
        private const val AMOUNT_LIMIT = 24
        /** A fast AI answer replaces the spinner directly; only a slower one shows the provisional draft first. */
        const val PROVISIONAL_DELAY_MS = 400L
    }
}
