package com.memento.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Renders [instant] as a UTC ISO-8601 calendar date, e.g. `2026-09-13`. */
fun formatIsoDate(instant: Instant): String = instant.toLocalDateTime(TimeZone.UTC).date.toString()

/** Parses a `yyyy-MM-dd` UTC calendar date at midnight, or returns `null` when malformed. */
fun parseIsoDateOrNull(text: String): Instant? {
    val date = try {
        LocalDate.parse(text)
    } catch (error: IllegalArgumentException) {
        return null
    }
    return date.atStartOfDayIn(TimeZone.UTC)
}
