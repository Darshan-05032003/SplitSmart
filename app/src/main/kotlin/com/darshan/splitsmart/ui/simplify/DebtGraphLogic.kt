package com.darshan.splitsmart.ui.simplify

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.split.DebtSimplifier
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A face on the debt graph: a member, or everyone past the cap folded into one. */
@Immutable
data class GraphNode(val id: String, val name: String)

/**
 * The "simplify debts" picture for a group. [before] is every IOU bill by bill (netted per pair), [after] the payments that
 * settle the same balances ([DebtSimplifier]). [shownBefore]/[shownAfter] are the same arrows on the [nodes] actually drawn:
 * past [DebtGraphLogic.MAX_NODES] people the smallest balances share one "+N" face ([hiddenPeople] of them).
 */
@Immutable
data class DebtGraph(
    val nodes: List<GraphNode>,
    val before: List<Debt>,
    val after: List<Debt>,
    val shownBefore: List<Debt>,
    val shownAfter: List<Debt>,
    val hiddenPeople: Int,
) {
    /** Everyone on the picture, the people folded into "+N" included. */
    val people: Int get() = nodes.size - (if (hiddenPeople > 0) 1 else 0) + hiddenPeople

    /** Transfers the simplification saves; the screen only offers the picture when this is positive. */
    val saves: Int get() = before.size - after.size
}

object DebtGraphLogic {
    const val MAX_NODES = 8
    const val OTHERS = "+others"

    /** Radius of the ring the faces sit on, as a fraction of the box (faces stay inside it). */
    const val RING = 0.36f

    private const val HALF = 0.5f

    /** Who owes whom bill by bill: each sharer owes the payer their share; opposite IOUs between two people cancel. */
    fun pairwise(bills: List<Bill>): List<Debt> {
        val owed = LinkedHashMap<Pair<String, String>, Long>()
        for (bill in bills) {
            val split = bill.split ?: continue
            val payer = bill.paidByMemberId ?: continue
            for (share in split.shares) {
                if (share.memberId == payer || share.totalPaise == 0L) continue
                val key = share.memberId to payer
                owed[key] = (owed[key] ?: 0L) + share.totalPaise
            }
        }
        val result = mutableListOf<Debt>()
        val seen = HashSet<Pair<String, String>>()
        for ((key, amount) in owed) {
            if (key in seen) continue
            val reverse = key.second to key.first
            seen += key
            seen += reverse
            val net = amount - (owed[reverse] ?: 0L)
            when {
                net > 0L -> result += Debt(key.first, key.second, net)
                net < 0L -> result += Debt(key.second, key.first, -net)
            }
        }
        return result.sortedWith(compareByDescending<Debt> { it.amountPaise }.thenBy { it.fromMemberId }.thenBy { it.toMemberId })
    }

    fun graph(members: List<Member>, bills: List<Bill>, maxNodes: Int = MAX_NODES): DebtGraph {
        val before = pairwise(bills)
        val balances = DebtSimplifier.balances(bills)
        val after = DebtSimplifier.simplify(balances)
        val names = members.associate { it.id to it.name }
        val involved = (before + after).flatMap { listOf(it.fromMemberId, it.toMemberId) }.distinct()
        val ordered = involved.sortedWith(compareByDescending<String> { abs(balances[it] ?: 0L) }.thenBy { names[it] ?: it }.thenBy { it })
        val folding = ordered.size > maxNodes
        val shown = if (folding) ordered.take(maxNodes - 1) else ordered
        val shownSet = shown.toSet()
        val hidden = ordered.size - shown.size
        val nodes = shown.map { GraphNode(it, names[it] ?: "") } + if (folding) listOf(GraphNode(OTHERS, "+$hidden")) else emptyList()
        fun fold(debts: List<Debt>): List<Debt> {
            if (!folding) return debts
            val merged = LinkedHashMap<Pair<String, String>, Long>()
            for (d in debts) {
                val from = if (d.fromMemberId in shownSet) d.fromMemberId else OTHERS
                val to = if (d.toMemberId in shownSet) d.toMemberId else OTHERS
                if (from == to) continue
                merged[from to to] = (merged[from to to] ?: 0L) + d.amountPaise
            }
            return merged.map { (k, v) -> Debt(k.first, k.second, v) }
        }
        return DebtGraph(nodes, before, after, fold(before), fold(after), hidden)
    }

    /** Where [count] faces sit, as fractions of a square box: on a ring, the first at twelve o'clock, then clockwise. */
    fun positions(count: Int): List<Offset> = List(count) { i ->
        val angle = -HALF * Math.PI + 2.0 * Math.PI * i / count
        Offset((HALF + RING * cos(angle)).toFloat(), (HALF + RING * sin(angle)).toFloat())
    }

    /**
     * The visible part of an arrow from [a] to [b] between two faces of [radius]: the line between their edges, cut to the part
     * between fractions [from] and [to] of it. Writes x1, y1, x2, y2 into [out]. Faces that touch leave a zero-length line.
     */
    fun trim(a: Offset, b: Offset, radius: Float, from: Float, to: Float, out: FloatArray) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)
        if (length <= radius * 2f || length == 0f) {
            val mx = (a.x + b.x) / 2f
            val my = (a.y + b.y) / 2f
            out[0] = mx; out[1] = my; out[2] = mx; out[3] = my
            return
        }
        val ux = dx / length
        val uy = dy / length
        val sx = a.x + ux * radius
        val sy = a.y + uy * radius
        val span = length - radius * 2f
        out[0] = sx + ux * span * from
        out[1] = sy + uy * span * from
        out[2] = sx + ux * span * to
        out[3] = sy + uy * span * to
    }

    /** First half of the animation: every IOU arrow shrinks into its middle. */
    fun collapse(progress: Float): Float = (progress / HALF).coerceIn(0f, 1f)

    /** Second half: the simplified payments draw from payer to payee. */
    fun draw(progress: Float): Float = ((progress - HALF) / HALF).coerceIn(0f, 1f)
}
