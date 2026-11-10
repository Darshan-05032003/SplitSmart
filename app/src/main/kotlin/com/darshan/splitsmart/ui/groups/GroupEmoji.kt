package com.darshan.splitsmart.ui.groups

import kotlin.math.abs

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

/** A group's tile emoji: from a keyword in its name ("Goa trip" → ✈️), otherwise a stable pick per name. */
fun groupEmoji(name: String): String {
    val lower = name.lowercase()
    return EMOJI_KEYWORDS.firstOrNull { (keys, _) -> keys.any { it in lower } }?.second
        ?: FALLBACK_EMOJI[abs(lower.hashCode() % FALLBACK_EMOJI.size)]
}
