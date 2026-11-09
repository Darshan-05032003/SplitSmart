package com.darshan.splitsmart.ui.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.darshan.splitsmart.R

/** Bricolage Grotesque's optical size axis runs 12-96. Display sizes get the tighter, quirkier cut; amounts in rows the sturdier one. */
private const val DISPLAY_OPSZ = 48f
private const val TEXT_OPSZ = 20f

private fun bricolage(weight: FontWeight, opticalSize: Float) = Font(
    resId = R.font.bricolage_grotesque_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.Setting("opsz", opticalSize)),
)

private fun figtree(weight: FontWeight) = Font(
    resId = R.font.figtree_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Headlines and the biggest numbers. Bricolage Grotesque at a display optical size. Has the ₹ glyph. */
val BricolageDisplay = FontFamily(
    bricolage(FontWeight.SemiBold, DISPLAY_OPSZ),
    bricolage(FontWeight.Bold, DISPLAY_OPSZ),
    bricolage(FontWeight.ExtraBold, DISPLAY_OPSZ),
)

/** Amounts in cards, rows and pills, and avatar initials. Bricolage Grotesque at a text optical size. */
val BricolageText = FontFamily(
    bricolage(FontWeight.SemiBold, TEXT_OPSZ),
    bricolage(FontWeight.Bold, TEXT_OPSZ),
    bricolage(FontWeight.ExtraBold, TEXT_OPSZ),
)

/** All UI text, and the sign of every amount. Has the ₹ glyph. */
val Figtree = FontFamily(
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
    figtree(FontWeight.Bold),
)

private val Centered = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Float = 0f, tabular: Boolean = false) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = Centered,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/**
 * The type scale from the design direction: hero 40/44, display 28/34, title 20/26, body 15/22, label 12/16.
 * Read it with `SplitSmartTheme.type`. Text follows the system font size; components are tested at 1.3×.
 */
@Immutable
data class SplitSmartType(
    /** The one big number on a screen. */
    val hero: TextStyle = style(BricolageDisplay, 40, 44, FontWeight.ExtraBold, tracking = -0.02f, tabular = true),
    /** Screen headlines on tab roots and empty states. */
    val display: TextStyle = style(BricolageDisplay, 28, 34, FontWeight.ExtraBold, tracking = -0.015f),
    /** Screen and section titles. */
    val title: TextStyle = style(BricolageDisplay, 20, 26, FontWeight.Bold, tracking = -0.005f),
    /** Amounts in cards and rows. */
    val amount: TextStyle = style(BricolageText, 18, 24, FontWeight.ExtraBold, tabular = true),
    /** Amounts inside pills and counters. */
    val amountSmall: TextStyle = style(BricolageText, 14, 18, FontWeight.Bold, tabular = true),
    val body: TextStyle = style(Figtree, 15, 22, FontWeight.Normal),
    val bodyStrong: TextStyle = style(Figtree, 15, 22, FontWeight.SemiBold),
    val button: TextStyle = style(Figtree, 16, 22, FontWeight.Bold, tracking = 0.005f),
    val label: TextStyle = style(Figtree, 12, 16, FontWeight.Medium, tracking = 0.01f),
    val labelStrong: TextStyle = style(Figtree, 12, 16, FontWeight.Bold, tracking = 0.02f),
    /** Tabular UI numerals for counts and small figures. */
    val numeric: TextStyle = style(Figtree, 15, 22, FontWeight.SemiBold, tabular = true),
)

val DefaultSplitSmartType = SplitSmartType()

/** Maps the scale onto Material slots, for anything that still reads `MaterialTheme.typography` under the Marigold look. */
internal fun SplitSmartType.toMaterialTypography(): Typography = Typography(
    displayLarge = hero,
    displayMedium = hero,
    displaySmall = display,
    headlineLarge = display,
    headlineMedium = display,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = bodyStrong,
    titleSmall = bodyStrong,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = label.copy(fontWeight = FontWeight.Normal),
    labelLarge = button,
    labelMedium = labelStrong,
    labelSmall = label,
)
