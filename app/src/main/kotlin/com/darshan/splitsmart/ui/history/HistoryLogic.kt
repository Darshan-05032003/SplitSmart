package com.darshan.splitsmart.ui.history

import androidx.compose.runtime.Immutable
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.Settlement
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One row of the history timeline: a bill someone paid, or a payment someone marked as made. */
@Immutable
sealed interface HistoryEntry {
    val key: String
    val at: Instant
    val paise: Long

    data class BillEntry(val bill: Bill, val payerName: String?) : HistoryEntry {
        override val key: String get() = "b:" + bill.id
        override val at: Instant get() = bill.createdAt
        override val paise: Long get() = bill.totalPaise
    }

    data class PaymentEntry(val settlement: Settlement) : HistoryEntry {
        override val key: String get() = "s:" + settlement.id
        override val at: Instant get() = settlement.createdAt
        override val paise: Long get() = settlement.amountPaise
    }
}

/** A month of the timeline, newest entry first, with what was spent and what was settled in it. */
@Immutable
data class HistoryMonth(val month: YearMonth, val entries: List<HistoryEntry>, val spentPaise: Long, val settledPaise: Long) {
    val key: String get() = "m:$month"
}

object HistoryLogic {
    /** Bills and payments merged newest first (payments first on a tie: they come after the bill they settle), in months of [zone]. */
    fun timeline(bills: List<Bill>, settlements: List<Settlement>, members: List<Member>, zone: ZoneId = ZoneId.systemDefault()): List<HistoryMonth> {
        val names = members.associate { it.id to it.name }
        val entries = ArrayList<HistoryEntry>(bills.size + settlements.size)
        settlements.mapTo(entries) { HistoryEntry.PaymentEntry(it) }
        bills.mapTo(entries) { HistoryEntry.BillEntry(it, it.paidByMemberId?.let(names::get)) }
        entries.sortWith(compareByDescending<HistoryEntry> { it.at }.thenBy { if (it is HistoryEntry.PaymentEntry) 0 else 1 }.thenBy { it.key })
        val months = ArrayList<HistoryMonth>()
        var current: YearMonth? = null
        var bucket = ArrayList<HistoryEntry>()
        fun close() {
            val month = current ?: return
            months += HistoryMonth(
                month,
                bucket,
                spentPaise = saturatingSum(bucket.filterIsInstance<HistoryEntry.BillEntry>().map { it.paise }),
                settledPaise = saturatingSum(bucket.filterIsInstance<HistoryEntry.PaymentEntry>().map { it.paise }),
            )
        }
        for (entry in entries) {
            val month = YearMonth.from(entry.at.atZone(zone))
            if (month != current) {
                close()
                current = month
                bucket = ArrayList()
            }
            bucket += entry
        }
        close()
        return months
    }
}

/** What a bill was for, read from the words in its name. A guess, so the card says so; anything unclear is Other. */
enum class BillCategory(val emoji: String, private val words: Set<String>) {
    Food("🍽️", setOf("dinner", "lunch", "breakfast", "brunch", "food", "cafe", "coffee", "chai", "tea", "restaurant", "pizza", "biryani", "snacks", "snack", "thali", "dosa", "drinks", "dessert", "bakery", "meal", "meals")),
    Travel("🚕", setOf("taxi", "cab", "auto", "fuel", "petrol", "diesel", "train", "flight", "bus", "ferry", "scooter", "metro", "toll", "parking", "travel", "trip")),
    Stay("🏠", setOf("hotel", "villa", "room", "hostel", "stay", "rent", "homestay", "resort", "lodge")),
    Groceries("🛒", setOf("grocery", "groceries", "market", "vegetables", "veggies", "milk", "supermarket", "fruits", "kirana")),
    Fun("🎟️", setOf("movie", "movies", "museum", "party", "games", "concert", "show", "tickets", "ticket", "club", "bowling")),
    Home("💡", setOf("electricity", "wifi", "internet", "gas", "water", "maintenance", "cleaning", "repair", "bill")),
    Other("🧾", emptySet());

    companion object {
        private val SPLIT = Regex("[^\\p{L}]+")

        fun of(title: String): BillCategory {
            val words = title.lowercase().split(SPLIT).filter { it.isNotEmpty() }
            return entries.firstOrNull { category -> words.any { it in category.words } } ?: Other
        }
    }
}

/** One bar on the insights card: [fraction] of the longest bar. */
@Immutable
data class InsightBar(val key: String, val label: String, val emoji: String?, val paise: Long, val fraction: Float)

@Immutable
data class Insights(
    val spentPaise: Long,
    val billCount: Int,
    val topPayers: List<InsightBar>,
    val topCategories: List<InsightBar>,
    val settledPaise: Long,
    val settledCount: Int,
)

object InsightsLogic {
    const val MAX_BARS = 5

    /** Who paid most (bills by payer), top categories (by the words in each bill's name) and what was settled. One pass per list. */
    fun of(bills: List<Bill>, members: List<Member>, settlements: List<Settlement>): Insights {
        val names = members.associate { it.id to it.name }
        val byPayer = HashMap<String, Long>()
        val byCategory = HashMap<BillCategory, Long>()
        var spent = 0L
        for (bill in bills) {
            spent = add(spent, bill.totalPaise)
            bill.paidByMemberId?.let { byPayer[it] = add(byPayer[it] ?: 0L, bill.totalPaise) }
            val category = BillCategory.of(bill.title)
            byCategory[category] = add(byCategory[category] ?: 0L, bill.totalPaise)
        }
        val payers = byPayer.entries
            .map { (id, paise) -> Triple(id, names[id] ?: "", paise) }
            .filter { it.second.isNotEmpty() && it.third > 0 }
            .sortedWith(compareByDescending<Triple<String, String, Long>> { it.third }.thenBy { it.second }.thenBy { it.first })
            .take(MAX_BARS)
        val categories = byCategory.entries.filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<BillCategory, Long>> { it.value }.thenBy { it.key.ordinal })
            .take(MAX_BARS)
        return Insights(
            spentPaise = spent,
            billCount = bills.size,
            topPayers = bars(payers.map { (id, name, paise) -> InsightBar(id, name, null, paise, 0f) }),
            topCategories = bars(categories.map { (category, paise) -> InsightBar(category.name.lowercase(), category.name, category.emoji, paise, 0f) }),
            settledPaise = saturatingSum(settlements.map { it.amountPaise }),
            settledCount = settlements.size,
        )
    }

    /** Each bar as a fraction of the longest. */
    private fun bars(rows: List<InsightBar>): List<InsightBar> {
        val max = rows.maxOfOrNull { it.paise } ?: return emptyList()
        return rows.map { it.copy(fraction = (it.paise.toDouble() / max).toFloat()) }
    }

    private fun add(a: Long, b: Long): Long = if (b > 0 && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b.coerceAtLeast(0)
}

internal fun saturatingSum(values: List<Long>): Long {
    var total = 0L
    for (v in values) total = if (v > 0 && total > Long.MAX_VALUE - v) Long.MAX_VALUE else total + v.coerceAtLeast(0)
    return total
}
