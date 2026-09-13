package com.memento.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Returns the local calendar date of [instant] in [timeZone]. */
fun localDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    instant.toLocalDateTime(timeZone).date

/** Renders the local calendar date of [instant] in [timeZone] as `yyyy-MM-dd`. */
fun formatIsoDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    localDate(instant, timeZone).toString()

/** Parses a `yyyy-MM-dd` date as start of day in [timeZone], or returns `null` when malformed. */
fun parseIsoDateOrNull(text: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant? {
    val date = try {
        LocalDate.parse(text)
    } catch (error: IllegalArgumentException) {
        return null
    }
    return date.atStartOfDayIn(timeZone)
}

/**
 * Returns the UTC-midnight epoch millis of [instant]'s local date in [timeZone].
 *
 * This is the representation Material3 `DatePicker` expects for `initialSelectedDateMillis`,
 * since the picker encodes the selected date as midnight UTC.
 */
fun localDatePickerMillis(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
    localDate(instant, timeZone).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * Resolves a date picked in a UTC-based picker to an [Instant] in [timeZone].
 *
 * [selectedUtcMillis] encodes the picked UTC date. The existing local time-of-day of [reference]
 * is preserved, so picking a date keeps the time of day and never shifts the calendar day.
 */
fun instantForPickedDate(
    selectedUtcMillis: Long,
    reference: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Instant {
    val pickedDate = Instant.fromEpochMilliseconds(selectedUtcMillis).toLocalDateTime(TimeZone.UTC).date
    val localTime = reference.toLocalDateTime(timeZone).time
    return LocalDateTime(pickedDate, localTime).toInstant(timeZone)
}
