package com.memento.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class IsoDateTest {

    private val tokyo = TimeZone.of("Asia/Tokyo")

    @Test
    fun rendersJapaneseYearMonthDay() {
        val instant = Instant.parse("2026-01-15T08:30:00Z")

        assertEquals("2026年1月15日", formatJournalDate(instant, tokyo))
    }

    @Test
    fun doesNotZeroPadMonthOrDay() {
        val instant = Instant.parse("2026-09-05T08:30:00Z")

        assertEquals("2026年9月5日", formatJournalDate(instant, tokyo))
    }

    @Test
    fun resolvesToTheLocalCalendarDay() {
        // 23:00 UTC is already the next day in Tokyo.
        val instant = Instant.parse("2026-09-12T23:30:00Z")

        assertEquals("2026年9月13日", formatJournalDate(instant, tokyo))
        assertEquals("2026年9月12日", formatJournalDate(instant, TimeZone.UTC))
    }
}
