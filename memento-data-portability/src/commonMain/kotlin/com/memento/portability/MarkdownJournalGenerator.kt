package com.memento.portability

/**
 * Renders a [MementoBackupV1] as a human- and LLM-friendly Markdown journal.
 *
 * The document starts with a title and a summary count, then contains one section per memento
 * ordered newest-first. Each section lists the structured metadata (date, place/brand/city,
 * coordinates, star rating, tasting notes, flavour tags and tags) followed by relative image
 * links into the archive's `media/` folder.
 */
object MarkdownJournalGenerator {

    private const val FILLED_STAR = '\u2605' // ★
    private const val EMPTY_STAR = '\u2606' // ☆
    private const val MAX_STARS = 5

    fun generate(backup: MementoBackupV1): String {
        val moments = backup.mementos.sortedByDescending { it.occurredAt }
        return buildString {
            appendLine("# Memento Journal")
            appendLine()
            appendLine("_${moments.size} ${if (moments.size == 1) "moment" else "moments"} exported._")
            appendLine()
            moments.forEachIndexed { index, moment ->
                appendMoment(moment)
                if (index != moments.lastIndex) {
                    appendLine("---")
                    appendLine()
                }
            }
        }
    }

    private fun StringBuilder.appendMoment(moment: BackupMemento) {
        appendLine("## ${moment.title.ifBlank { "Untitled moment" }}")
        appendLine()
        appendLine("**Date:** ${moment.occurredAt}")

        moment.place?.let { place ->
            appendLine("**Place:** ${place.name}")
            place.brand?.takeIf { it.isNotBlank() }?.let { appendLine("**Brand:** $it") }
            place.neighborhood?.takeIf { it.isNotBlank() }?.let { appendLine("**Neighborhood:** $it") }
            place.city?.takeIf { it.isNotBlank() }?.let { appendLine("**City:** $it") }
            place.country?.takeIf { it.isNotBlank() }?.let { appendLine("**Country:** $it") }
        }

        moment.coordinates?.let { coordinates ->
            val accuracy = coordinates.accuracyMeters?.let { " (\u00b1${formatNumber(it)} m)" }.orEmpty()
            appendLine(
                "**Coordinates:** ${formatNumber(coordinates.latitude)}, " +
                    "${formatNumber(coordinates.longitude)}$accuracy"
            )
        }

        moment.rating?.let { appendLine("**Rating:** ${stars(it.stars)} (${it.stars}/$MAX_STARS)") }

        moment.tastingNotes?.let { notes ->
            if (notes.text.isNotBlank()) {
                appendLine("**Tasting notes:** ${notes.text}")
            }
            if (notes.flavorTags.isNotEmpty()) {
                appendLine("**Flavour tags:** ${notes.flavorTags.joinToString(", ")}")
            }
        }

        if (moment.tags.isNotEmpty()) {
            appendLine("**Tags:** ${moment.tags.joinToString(" ") { "#${it.replace(" ", "-")}" }}")
        }

        if (moment.collections.isNotEmpty()) {
            appendLine("**Collections:** ${moment.collections.joinToString(", ")}")
        }

        if (moment.reflection.isNotBlank()) {
            appendLine()
            appendLine(moment.reflection)
        }

        if (moment.media.isNotEmpty()) {
            appendLine()
            moment.media.forEach { media ->
                appendLine("![](media/${media.mediaRefId})")
            }
        }

        appendLine()
    }

    private fun stars(count: Int): String {
        val filled = count.coerceIn(0, MAX_STARS)
        return FILLED_STAR.toString().repeat(filled) + EMPTY_STAR.toString().repeat(MAX_STARS - filled)
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
