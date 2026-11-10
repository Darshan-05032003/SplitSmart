package com.darshan.splitsmart.ui.designsystem.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing on the 4/8 grid. Screens use these instead of literal dp. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
    val giant = 64.dp

    /** Horizontal gutter of every screen. */
    val screen = 16.dp
}

/**
 * Marigold Mela's three radii plus a full pill. Chunky and friendly: nothing tappable has a corner under 16dp.
 * Small: fields, chips' containers, snackbars. Medium: buttons, cards, rows. Large: sheets, hero cards, the bottom bar.
 */
object Radii {
    val small = 16.dp
    val medium = 24.dp
    val large = 32.dp

    val smallShape = RoundedCornerShape(small)
    val mediumShape = RoundedCornerShape(medium)
    val largeShape = RoundedCornerShape(large)
    val topLargeShape = RoundedCornerShape(topStart = large, topEnd = large)
    val pillShape = RoundedCornerShape(percent = 50)
    val circleShape = CircleShape
}

/**
 * Depth is a soft solid offset shadow, not a blur: things sit on the page like stickers and press down into it.
 * Each value is how far the shadow sits below its element at rest; pressing closes the gap.
 */
object Depth {
    val none = 0.dp
    val chip = 2.dp
    val card = 3.dp
    val button = 4.dp
    val fab = 5.dp
}

/** Stroke widths. */
object Strokes {
    val hairline = 1.dp
    val outline = 1.5.dp
    val focus = 2.dp

    /** The soft glow around a focused field. */
    val focusGlow = 4.dp

    /** The camera's viewfinder corners. */
    val bracket = 5.dp

    /** The scan line sweeping the viewfinder. */
    val beam = 3.dp

    /** The split donut's ring, on the split screen and (smaller) on a bill. */
    val donut = 22.dp
    val donutSmall = 12.dp

    /** Arrows on the simplify-debts graph: the IOUs before, the payments after. */
    val graphEdge = 2.dp
    val graphEdgeBold = 4.dp

    /** The border of the "Settled" stamp. */
    val stamp = 3.dp

    /** The history timeline's rail. */
    val rail = 2.dp
}

/** Fixed component sizes. */
object Sizes {
    /** Minimum touch target of anything tappable. */
    val touchTarget = 48.dp
    val buttonHeight = 56.dp
    val buttonHeightCompact = 48.dp
    val fieldHeight = 56.dp
    val iconSmall = 16.dp
    val icon = 24.dp
    val iconLarge = 32.dp
    val avatarSmall = 32.dp
    val avatar = 40.dp
    val avatarLarge = 56.dp
    val avatarHero = 72.dp

    /** Gap between avatars once a stack has fanned out. */
    val avatarFanGap = 6.dp

    /** The ring that separates overlapping avatars. */
    val avatarRing = 2.dp
    val chipHeight = 36.dp
    val pillHeight = 28.dp
    val counterBadge = 22.dp
    val statusDot = 8.dp
    val fab = 64.dp
    val topBarHeight = 64.dp
    val listRowHeight = 72.dp
    val listRowCompact = 56.dp
    val bottomBarHeight = 68.dp
    val bottomBarBlobHeight = 40.dp
    val bottomBarInset = 12.dp
    /** Tallest a list inside a bottom sheet grows before it scrolls (the bar's group picker). */
    val sheetListMax = 360.dp
    val sheetHandleWidth = 44.dp
    val sheetHandleHeight = 5.dp
    val snackbarMinHeight = 56.dp
    val snackbarTimerHeight = 3.dp
    val skeletonLine = 14.dp
    val skeletonTitle = 18.dp
    val illustration = 148.dp
    val illustrationSmall = 96.dp
    /** Loading placeholders the size of a hero card and of a group card. */
    val heroSkeleton = 168.dp
    val cardSkeleton = 128.dp
    val loader = 48.dp
    val loaderSmall = 24.dp
    val loadingDot = 6.dp

    /** The camera's shutter and the disc inside its ring. */
    val shutter = 76.dp
    val shutterInner = 58.dp

    /** One arm of a viewfinder corner bracket. */
    val viewfinderBracket = 36.dp

    /** Corner of the outline drawn around a line of text the camera sees (not tappable, so under the 16dp rule). */
    val textOutlineCorner = 6.dp

    /** The split donut on the split screen and on a bill. */
    val donut = 148.dp
    val donutSmall = 96.dp

    /** Height of the simplify-debts graph: eight faces on a ring with room for their names. */
    val debtGraph = 300.dp

    /** A UPI QR code: big enough for a phone held at arm's length to read. */
    val qrCode = 232.dp

    /** Height of one bar on the insights card. */
    val insightBar = 12.dp

    /** The dot on the history timeline's rail. */
    val railDot = 12.dp
}

/**
 * Discovery point for the static tokens. `SplitSmartTokens.spacing.md` and `Spacing.md` are the same value; colours, type and
 * motion depend on the theme and are read through `SplitSmartTheme.colors`, `.type` and `.motion`.
 */
object SplitSmartTokens {
    val spacing = Spacing
    val radii = Radii
    val depth = Depth
    val strokes = Strokes
    val sizes = Sizes
}
