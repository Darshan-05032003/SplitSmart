package com.darshan.splitsmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.R
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.abs

/** Brand icons that are not in material-icons-core live as vector drawables. */
object AppIcons {
    val Receipt: Int = R.drawable.ic_receipt
    val Camera: Int = R.drawable.ic_camera
    val Gallery: Int = R.drawable.ic_gallery
    val Qr: Int = R.drawable.ic_qr
    val Sparkle: Int = R.drawable.ic_sparkle
    val Group: Int = R.drawable.ic_group
    val Split: Int = R.drawable.ic_split
    val EditNote: Int = R.drawable.ic_edit_note
    val Wallet: Int = R.drawable.ic_wallet
}

@Composable
fun appIcon(@DrawableRes id: Int): Painter = painterResource(id)

/** First letter of a name in a tinted circle; the hue comes from the name so a person is recognisable everywhere. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, ring: Color? = null) {
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val (bg, fg) = avatarColors(name)
    Box(
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = fg, style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall)
    }
}

private val AVATAR_HUES = listOf(
    Color(0xFFFFDBD0) to Color(0xFF8C2A0D),
    Color(0xFFDBE1FF) to Color(0xFF33446D),
    Color(0xFFFFDEA6) to Color(0xFF5E4200),
    Color(0xFFC7F0D4) to Color(0xFF00522B),
    Color(0xFFF3DAFF) to Color(0xFF5B2C73),
    Color(0xFFCDEDF5) to Color(0xFF004E5C),
)

fun avatarColors(name: String): Pair<Color, Color> = AVATAR_HUES[abs(name.trim().lowercase().hashCode()) % AVATAR_HUES.size]

/** Overlapping avatars for a group's members, "+N" when there are more than [max]. */
@Composable
fun AvatarStack(names: List<String>, modifier: Modifier = Modifier, max: Int = 4, size: Dp = 28.dp) {
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        names.take(max).forEachIndexed { i, n ->
            InitialAvatar(n, size = size, ring = ring, modifier = Modifier.offset(x = (-8 * i).dp))
        }
        if (names.size > max) {
            Box(
                Modifier.offset(x = (-8 * max).dp).size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Text("+${names.size - max}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** An emoji tile for a group, picked from keywords in the name (trip, flat, dinner…), coloured by the name. */
@Composable
fun GroupAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val (bg, _) = avatarColors(name)
    Box(modifier.size(size).clip(RoundedCornerShape(16.dp)).background(bg).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(groupEmoji(name), style = MaterialTheme.typography.headlineSmall)
    }
}

private val EMOJI_KEYWORDS = listOf(
    listOf("trip", "travel", "goa", "manali", "vacation", "holiday", "tour") to "✈️",
    listOf("beach") to "🏖️",
    listOf("flat", "home", "house", "room", "pg", "rent") to "🏠",
    listOf("dinner", "lunch", "food", "brunch", "party", "restaurant") to "🍽️",
    listOf("coffee", "cafe", "chai") to "☕",
    listOf("office", "work", "team") to "💼",
    listOf("movie", "film") to "🎬",
    listOf("birthday", "bday") to "🎂",
    listOf("grocer", "market") to "🛒",
)
private val FALLBACK_EMOJI = listOf("🎉", "🍕", "🌴", "🎲", "⭐", "🧾")

fun groupEmoji(name: String): String {
    val lower = name.lowercase()
    return EMOJI_KEYWORDS.firstOrNull { (keys, _) -> keys.any { it in lower } }?.second
        ?: FALLBACK_EMOJI[abs(lower.hashCode()) % FALLBACK_EMOJI.size]
}

/** Small indigo pill marking anything the AI produced. */
@Composable
fun AiBadge(modifier: Modifier = Modifier, label: String = stringResource(R.string.badge_ai)) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(appIcon(AppIcons.Sparkle), contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

enum class BalanceTone { OWED, OWE, NEUTRAL }

/** Rounded chip for a money state: green when you are owed, red when you owe, neutral otherwise. */
@Composable
fun BalanceChip(text: String, tone: BalanceTone, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val (bg, fg) = when (tone) {
        BalanceTone.OWED -> colors.owed.container to colors.owed.content
        BalanceTone.OWE -> colors.owe.container to colors.owe.content
        BalanceTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = CircleShape, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Balance text for "you": positive → owed, negative → owe, zero → settled. */
@Composable
fun yourBalanceLabel(balancePaise: Long): Pair<String, BalanceTone> = when {
    balancePaise > 0 -> stringResource(R.string.balance_you_are_owed, Money.formatInr(balancePaise)) to BalanceTone.OWED
    balancePaise < 0 -> stringResource(R.string.balance_you_owe, Money.formatInr(-balancePaise)) to BalanceTone.OWE
    else -> stringResource(R.string.balance_settled) to BalanceTone.NEUTRAL
}

/** A section title with an optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** Icon inside a soft tinted circle (list leading icons, illustrations). */
@Composable
fun IconBubble(painter: Painter, modifier: Modifier = Modifier, size: Dp = 40.dp, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun IconBubble(vector: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(vector, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** The coral gradient card used for headline numbers. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(SplitSmartTheme.colors.heroGradient)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

enum class BannerTone { INFO, AI, WARNING, ERROR, SUCCESS }

/** Tinted card with a leading icon: notices, AI state, warnings. */
@Composable
fun InfoBanner(
    tone: BannerTone,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        BannerTone.INFO -> scheme.surfaceContainerHigh to scheme.onSurface
        BannerTone.AI -> scheme.secondaryContainer to scheme.onSecondaryContainer
        BannerTone.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        BannerTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        BannerTone.SUCCESS -> SplitSmartTheme.colors.owed.container to SplitSmartTheme.colors.owed.content
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg), shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp).padding(top = 1.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** Label/value row used in totals and breakdowns. */
@Composable
fun AmountRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** Card with the standard 16 dp inner padding. */
@Composable
fun ContentCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

/** Composite illustration for empty states: a receipt in a big soft circle with an AI sparkle and a people badge. */
@Composable
fun EmptyIllustration(modifier: Modifier = Modifier, main: Painter = appIcon(AppIcons.Receipt)) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(168.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(scheme.primaryContainer))
        Box(Modifier.size(104.dp).clip(CircleShape).background(scheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            Icon(main, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(56.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 10.dp)) {
            IconBubble(appIcon(AppIcons.Sparkle), size = 44.dp, container = scheme.secondaryContainer, content = scheme.onSecondaryContainer)
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x = 6.dp, y = (-8).dp)) {
            IconBubble(appIcon(AppIcons.Group), size = 44.dp, container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
        }
    }
}
