package com.darshan.splitsmart.ui.simplify

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.ChunkyCard
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.groupdetail.DebtRow
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Test tags of the simplify-debts card, sheet and graph. */
object SimplifyTags {
    const val CARD = "simplify_card"
    const val SHEET = "simplify_sheet"
    const val GRAPH = "simplify_graph"
    const val FACE = "simplify_face"
    const val REPLAY = "simplify_replay"
}

/** Spread of an arrowhead either side of the line, in radians, and its length as a multiple of the stroke. */
private const val HEAD_SPREAD = 0.5f
private const val HEAD_LENGTH = 3.5f
private const val HEAD_SHARE = 0.4f

/** How faded the old IOUs are before they collapse: they are the "before", the payments are the point. */
private const val BEFORE_ALPHA = 0.55f

/** The end of the collapse phase (see [DebtGraphLogic.collapse]). */
private const val HALF = 0.5f

/** The amount labels fade in over the last part of the drawing phase. */
private const val LABEL_FADE_FROM = 0.8f

/**
 * The simplify-debts picture. Faces sit on a ring; first every IOU bill by bill is a thin arrow from who owes to who paid, then
 * the arrows collapse into their middles and the few payments that settle the same balances draw themselves in from payer to
 * payee, their amounts fading in on top. It plays once per [playKey] after a short beat, all in draw (the faces are the only
 * composables). Reduced motion turns it into a short cross-fade to the result.
 */
@Composable
fun DebtGraphView(graph: DebtGraph, playKey: Int, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val progress = remember { Animatable(0f) }
    LaunchedEffect(playKey) {
        // A beat to see the tangle, the collapse, a beat, the payments drawing in: about 1.3 s, interrupted by Play again.
        progress.snapTo(0f)
        val beat = if (motion.reduced) 0L else MotionDurations.SequenceStep.toLong()
        delay(beat)
        progress.animateTo(HALF, motion.spec(MotionToken.Screen))
        delay(beat)
        progress.animateTo(1f, motion.spec(MotionToken.Screen))
    }
    val description = stringResource(
        R.string.simplify_graph_description,
        pluralStringResource(R.plurals.simplify_ious, graph.before.size, graph.before.size),
        pluralStringResource(R.plurals.simplify_people, graph.people, graph.people),
        pluralStringResource(R.plurals.simplify_payments, graph.after.size, graph.after.size),
    )
    val measurer = rememberTextMeasurer()
    val labelStyle = type.labelStrong.copy(color = colors.onPrimary)
    val beforeColor = colors.textSecondary
    val afterColor = colors.accent
    val labelBg = colors.primary
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(Sizes.debtGraph)
            .testTag(SimplifyTags.GRAPH)
            .semantics { contentDescription = description },
    ) {
        val side = minOf(constraints.maxWidth, constraints.maxHeight).toFloat()
        val originX = (constraints.maxWidth - side) / 2f
        val originY = (constraints.maxHeight - side) / 2f
        val points = remember(graph.nodes.size, side, originX, originY) {
            DebtGraphLogic.positions(graph.nodes.size).map { Offset(originX + it.x * side, originY + it.y * side) }
        }
        val index = remember(graph) { graph.nodes.withIndex().associate { it.value.id to it.index } }
        Box(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    // Arrows stop short of the faces and their names.
                    val radius = Sizes.avatar.toPx() / 2f + Spacing.sm.toPx()
                    val thin = Strokes.graphEdge.toPx()
                    val bold = Strokes.graphEdgeBold.toPx()
                    val labels = graph.shownAfter.map { measurer.measure(AmountFormat.format(it.amountPaise), labelStyle) }
                    val pad = Spacing.xxs.toPx()
                    val corner = CornerRadius(Radii.small.toPx())
                    val out = FloatArray(4)
                    val head = Path()
                    onDrawBehind {
                        val p = progress.value
                        val collapse = DebtGraphLogic.collapse(p)
                        val drawn = DebtGraphLogic.draw(p)
                        if (collapse < 1f) {
                            for (debt in graph.shownBefore) {
                                val a = points.getOrNull(index[debt.fromMemberId] ?: -1) ?: continue
                                val b = points.getOrNull(index[debt.toMemberId] ?: -1) ?: continue
                                DebtGraphLogic.trim(a, b, radius, collapse / 2f, 1f - collapse / 2f, out)
                                arrow(out, beforeColor.copy(alpha = BEFORE_ALPHA * (1f - collapse)), thin, head)
                            }
                        }
                        if (drawn > 0f) {
                            graph.shownAfter.forEachIndexed { i, debt ->
                                val a = points.getOrNull(index[debt.fromMemberId] ?: -1) ?: return@forEachIndexed
                                val b = points.getOrNull(index[debt.toMemberId] ?: -1) ?: return@forEachIndexed
                                DebtGraphLogic.trim(a, b, radius, 0f, drawn, out)
                                arrow(out, afterColor, bold, head)
                                val fade = ((drawn - LABEL_FADE_FROM) / (1f - LABEL_FADE_FROM)).coerceIn(0f, 1f)
                                if (fade > 0f) {
                                    val layout = labels[i]
                                    val cx = (a.x + b.x) / 2f
                                    val cy = (a.y + b.y) / 2f
                                    val w = layout.size.width + pad * 4
                                    val h = layout.size.height + pad * 2
                                    drawRoundRect(labelBg, Offset(cx - w / 2f, cy - h / 2f), Size(w, h), corner, alpha = fade)
                                    translate(cx - layout.size.width / 2f, cy - layout.size.height / 2f) { drawText(layout, alpha = fade) }
                                }
                            }
                        }
                    }
                },
        )
        graph.nodes.forEachIndexed { i, node ->
            val point = points[i]
            GraphFace(node, Modifier.offset { IntOffset((point.x).toInt(), (point.y).toInt()) })
        }
    }
}

/** A line with an arrowhead at its end, from the four numbers [DebtGraphLogic.trim] wrote. Reuses [head]. */
private fun DrawScope.arrow(out: FloatArray, color: Color, stroke: Float, head: Path) {
    val start = Offset(out[0], out[1])
    val end = Offset(out[2], out[3])
    val span = (end - start).getDistance()
    if (span < stroke) return
    drawLine(color, start, end, stroke, cap = StrokeCap.Round)
    val angle = atan2(end.y - start.y, end.x - start.x)
    // The head shrinks with a collapsing arrow instead of hanging on as a loose triangle.
    val length = minOf(stroke * HEAD_LENGTH, span * HEAD_SHARE)
    head.reset()
    head.moveTo(end.x, end.y)
    head.lineTo(end.x - length * cos(angle - HEAD_SPREAD), end.y - length * sin(angle - HEAD_SPREAD))
    head.lineTo(end.x - length * cos(angle + HEAD_SPREAD), end.y - length * sin(angle + HEAD_SPREAD))
    head.close()
    drawPath(head, color)
}

/** A face on the ring, centred on its point, with its name under it; "+N" for everyone folded together. */
@Composable
private fun GraphFace(node: GraphNode, modifier: Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val half = Sizes.avatar / 2
    Column(
        modifier
            .offset(x = -(Sizes.avatarLarge + Spacing.md) / 2, y = -half)
            .width(Sizes.avatarLarge + Spacing.md)
            .testTag(SimplifyTags.FACE)
            .clearAndSetSemantics {}, // the graph says it all in one sentence
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (node.id == DebtGraphLogic.OTHERS) {
            Box(Modifier.size(Sizes.avatar).background(colors.raised, Radii.circleShape), contentAlignment = Alignment.Center) {
                BasicText(node.name, style = type.labelStrong.copy(color = colors.text))
            }
        } else {
            MemberAvatar(node.name, size = Sizes.avatar, ring = true, contentDescription = null)
            BasicText(
                DisplayText.safe(node.name),
                style = type.label.copy(color = colors.text, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** On the group screen, above who owes whom: how many payments the simplification saves. Opens the picture. */
@Composable
fun SimplifyCard(graph: DebtGraph, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val title = stringResource(
        R.string.simplify_card_title,
        pluralStringResource(R.plurals.simplify_payments, graph.after.size, graph.after.size),
        pluralStringResource(R.plurals.simplify_ious, graph.before.size, graph.before.size),
    )
    ChunkyCard(
        modifier.fillMaxWidth().testTag(SimplifyTags.CARD),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.simplify_card_action),
        color = colors.info.container,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BasicText("✨", Modifier.clearAndSetSemantics {}, style = type.title.copy(color = colors.info.content))
            Column(Modifier.weight(1f)) {
                BasicText(title, style = type.bodyStrong.copy(color = colors.info.content))
                BasicText(stringResource(R.string.simplify_card_body), style = type.label.copy(color = colors.info.content))
            }
            BasicText("→", Modifier.clearAndSetSemantics {}, style = type.title.copy(color = colors.info.content))
        }
    }
}

/** The sheet: the picture, a sentence that says what happened, Play again, and the payments in words. */
@Composable
fun SimplifySheetContent(graph: DebtGraph, nameOf: (String) -> String, playKey: Int, onReplay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(
        modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.lg).testTag(SimplifyTags.SHEET),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.simplify_title), Modifier.weight(1f).semantics { heading() }, style = type.title.copy(color = colors.text))
            TextAction(stringResource(R.string.simplify_replay), onReplay, Modifier.testTag(SimplifyTags.REPLAY))
        }
        BasicText(
            stringResource(
                R.string.simplify_body,
                pluralStringResource(R.plurals.simplify_ious, graph.before.size, graph.before.size),
                pluralStringResource(R.plurals.simplify_payments, graph.after.size, graph.after.size),
            ),
            style = type.body.copy(color = colors.textSecondary),
        )
        DebtGraphView(graph, playKey)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LegendKey(colors.textSecondary.copy(alpha = BEFORE_ALPHA), Strokes.graphEdge, stringResource(R.string.simplify_legend_before))
            LegendKey(colors.accent, Strokes.graphEdgeBold, stringResource(R.string.simplify_legend_after))
        }
        if (graph.hiddenPeople > 0) {
            BasicText(pluralStringResource(R.plurals.simplify_folded, graph.hiddenPeople, graph.hiddenPeople), style = type.label.copy(color = colors.textSecondary))
        }
        graph.after.forEach { debt: Debt -> DebtRow(nameOf(debt.fromMemberId), nameOf(debt.toMemberId), debt.amountPaise) }
    }
}

@Composable
private fun LegendKey(color: Color, stroke: Dp, label: String) {
    val type = SplitSmartTheme.type
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.width(Spacing.lg).height(stroke).background(color, Radii.pillShape))
        BasicText(label, style = type.label.copy(color = SplitSmartTheme.colors.textSecondary))
    }
}
