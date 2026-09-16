package com.memento.presentation

/**
 * Canonical flavour options offered while recording a keepsake.
 *
 * These are plain labels on purpose: the bundled Noto Sans JP font has no emoji glyphs, so
 * decorative emoji (previously "Cold 🧊" / "Hot 🔥") rendered as tofu boxes on web.
 */
val QUICK_FLAVORS: List<String> = listOf(
    "Cold",
    "Hot",
    "Unsweetened (無糖)",
    "Low Sugar (微糖)",
    "Sweet",
    "Fizzy (炭酸)",
)

/** Flavour labels persisted before emoji were stripped, keyed case-insensitively. */
private val LEGACY_FLAVOR_ALIASES: Map<String, String> = mapOf(
    "cold 🧊" to "Cold",
    "hot 🔥" to "Hot",
)

/**
 * Canonicalises a stored flavour label. Legacy values that carried a trailing emoji are mapped to
 * their canonical form so existing keepsakes display and edit cleanly.
 */
fun normalizeFlavorTag(value: String): String {
    val trimmed = value.trim()
    return LEGACY_FLAVOR_ALIASES[trimmed.lowercase()] ?: trimmed
}
