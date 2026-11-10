package com.darshan.splitsmart.ui.simplify

import androidx.compose.ui.geometry.Offset
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testMember
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class DebtGraphLogicTest {
    private fun bill(id: String, payer: String, shares: Map<String, Long>): Bill {
        val total = shares.values.sum()
        return testBill(id = id, totalPaise = total, items = emptyList(), paidByMemberId = payer, status = BillStatus.SPLIT).copy(
            split = BillSplit(SplitConfig(SplitMode.EQUAL, shares.keys.toList()), shares.map { (m, p) -> MemberShare(m, p, 0, 0, 0) }),
        )
    }

    // A chain and a loop: Asha paid for Ravi, Ravi paid for Meera, Meera paid for Asha, Darshan paid for everyone.
    private val bills = listOf(
        bill("b1", "a", mapOf("a" to 100L, "r" to 300L)),
        bill("b2", "r", mapOf("r" to 100L, "m" to 300L)),
        bill("b3", "m", mapOf("m" to 100L, "a" to 100L)),
        bill("b4", "d", mapOf("d" to 100L, "a" to 100L, "r" to 100L, "m" to 100L)),
    )
    private val members = listOf(testMember("a", name = "Asha"), testMember("r", name = "Ravi"), testMember("m", name = "Meera"), testMember("d", name = "Darshan"))

    @Test
    fun `bill by bill IOUs are netted per pair, skipping bills without a split or a payer`() {
        val pairwise = DebtGraphLogic.pairwise(bills + testBill(id = "draft", totalPaise = 999) + bill("nopayer", "a", mapOf("r" to 50L)).copy(paidByMemberId = null))
        assertThat(pairwise).containsExactly(
            Debt("r", "a", 300),
            Debt("m", "r", 300),
            Debt("a", "m", 100),
            Debt("a", "d", 100),
            Debt("r", "d", 100),
            Debt("m", "d", 100),
        )
        // Opposite IOUs cancel.
        val both = listOf(bill("x", "a", mapOf("r" to 500L)), bill("y", "r", mapOf("a" to 200L)))
        assertThat(DebtGraphLogic.pairwise(both)).containsExactly(Debt("r", "a", 300))
        assertThat(DebtGraphLogic.pairwise(listOf(bill("z", "a", mapOf("r" to 200L)), bill("w", "r", mapOf("a" to 200L))))).isEmpty()
    }

    @Test
    fun `the graph shows every IOU before and the simplified payments after, with the same net for everyone`() {
        val graph = DebtGraphLogic.graph(members, bills)
        assertThat(graph.before).hasSize(6)
        assertThat(graph.after).isEqualTo(DebtSimplifier.simplify(DebtSimplifier.balances(bills)))
        assertThat(graph.after.size).isLessThan(graph.before.size)
        assertThat(graph.saves).isEqualTo(graph.before.size - graph.after.size)
        fun net(debts: List<Debt>) = buildMap<String, Long> { debts.forEach { merge(it.fromMemberId, -it.amountPaise, Long::plus); merge(it.toMemberId, it.amountPaise, Long::plus) } }.filterValues { it != 0L }
        assertThat(net(graph.before)).isEqualTo(net(graph.after))
        // Biggest balance first (Darshan is owed ₹3, Meera owes ₹3), ties by name.
        assertThat(graph.nodes.map { it.name }).containsExactly("Darshan", "Meera", "Asha", "Ravi").inOrder()
        assertThat(graph.hiddenPeople).isEqualTo(0)
    }

    @Test
    fun `200 people fold into seven faces and one others node, money still balanced`() {
        val random = Random(42)
        val people = (1..200).map { testMember("p$it", name = "Person $it") }
        val manyBills = (1..300).map { n ->
            val payer = people[random.nextInt(people.size)].id
            val sharers = people.shuffled(random).take(random.nextInt(2, 6)).associate { it.id to random.nextLong(1, 5_000) }
            bill("b$n", payer, sharers)
        }
        val graph = DebtGraphLogic.graph(people, manyBills)
        assertThat(graph.nodes).hasSize(DebtGraphLogic.MAX_NODES)
        assertThat(graph.nodes.last().id).isEqualTo(DebtGraphLogic.OTHERS)
        assertThat(graph.hiddenPeople).isGreaterThan(0)
        val ids = graph.nodes.map { it.id }.toSet()
        (graph.shownBefore + graph.shownAfter).forEach { edge ->
            assertThat(ids).containsAtLeast(edge.fromMemberId, edge.toMemberId)
            assertThat(edge.fromMemberId).isNotEqualTo(edge.toMemberId)
        }
        // The full lists stay exact even though the picture is folded.
        assertThat(graph.after).isEqualTo(DebtSimplifier.simplify(DebtSimplifier.balances(manyBills)))
    }

    @Test
    fun `nodes sit on a circle inside the box, starting at the top`() {
        val points = DebtGraphLogic.positions(4)
        assertThat(points[0].x).isWithin(1e-4f).of(0.5f)
        assertThat(points[0].y).isLessThan(0.5f)
        points.forEach { p ->
            assertThat(p.x).isIn(com.google.common.collect.Range.closed(0f, 1f))
            assertThat(p.y).isIn(com.google.common.collect.Range.closed(0f, 1f))
            assertThat(hypot(p.x - 0.5f, p.y - 0.5f)).isWithin(1e-4f).of(DebtGraphLogic.RING)
        }
        assertThat(DebtGraphLogic.positions(1).single()).isEqualTo(Offset(0.5f, 0.5f - DebtGraphLogic.RING))
        assertThat(DebtGraphLogic.positions(0)).isEmpty()
    }

    @Test
    fun `an arrow stops at both faces' edges, and a collapsing arrow shrinks to its middle`() {
        val out = FloatArray(4)
        DebtGraphLogic.trim(Offset(0f, 0f), Offset(100f, 0f), radius = 10f, from = 0f, to = 1f, out = out)
        assertThat(out.toList()).containsExactly(10f, 0f, 90f, 0f).inOrder()
        DebtGraphLogic.trim(Offset(0f, 0f), Offset(100f, 0f), radius = 10f, from = 0f, to = 0.5f, out = out)
        assertThat(out[2]).isWithin(1e-3f).of(50f)
        // Collapse toward the middle: both ends meet at 50.
        DebtGraphLogic.trim(Offset(0f, 0f), Offset(100f, 0f), radius = 10f, from = 0.5f, to = 0.5f, out = out)
        assertThat(out[0]).isWithin(1e-3f).of(50f)
        assertThat(out[2]).isWithin(1e-3f).of(50f)
        // Faces that overlap leave no arrow at all.
        DebtGraphLogic.trim(Offset(0f, 0f), Offset(15f, 0f), radius = 10f, from = 0f, to = 1f, out = out)
        assertThat(out[0]).isEqualTo(out[2])
    }

    @Test
    fun `the two phases of the animation split the progress in half`() {
        assertThat(DebtGraphLogic.collapse(0f)).isEqualTo(0f)
        assertThat(DebtGraphLogic.collapse(0.5f)).isEqualTo(1f)
        assertThat(DebtGraphLogic.collapse(1f)).isEqualTo(1f)
        assertThat(DebtGraphLogic.draw(0.5f)).isEqualTo(0f)
        assertThat(DebtGraphLogic.draw(0.75f)).isEqualTo(0.5f)
        assertThat(DebtGraphLogic.draw(1f)).isEqualTo(1f)
    }
}
