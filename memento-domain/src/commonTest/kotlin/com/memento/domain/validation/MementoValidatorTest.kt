package com.memento.domain.validation

import com.memento.domain.memento
import com.memento.domain.model.Place
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MementoValidatorTest {

    @Test
    fun validMementoHasNoViolations() {
        val valid = memento(title = "Morning coffee")

        assertContentEquals(emptyList(), MementoValidator.validate(valid))
        assertTrue(MementoValidator.isValid(valid))
    }

    @Test
    fun titleBlankAndNoPlaceYieldsTitleViolation() {
        val invalid = memento(title = "", place = null)

        val violations = MementoValidator.validate(invalid)

        assertTrue(violations.any { it.field == "title" })
        assertFalse(violations.any { it.field == "media" })
        assertFalse(MementoValidator.isValid(invalid))
    }

    @Test
    fun blankTitleWithPlaceIsAllowed() {
        val valid = memento(title = "  ", place = Place("Tokyo Station"))

        assertFalse(MementoValidator.validate(valid).any { it.field == "title" })
    }

    @Test
    fun noMediaYieldsMediaViolation() {
        val invalid = memento(media = emptyList())

        val violations = MementoValidator.validate(invalid)

        assertEquals(1, violations.count { it.field == "media" })
        assertFalse(MementoValidator.isValid(invalid))
    }

    @Test
    fun badCurrencyCodeYieldsViolation() {
        val invalid = memento(currencyCode = "US")

        val violation = MementoValidator.validate(invalid).single { it.field == "currencyCode" }

        assertEquals("currencyCode", violation.field)
        assertTrue(violation.message.isNotBlank())
    }

    @Test
    fun threeLetterCurrencyCodeIsAllowed() {
        val valid = memento(currencyCode = "USD")

        assertFalse(MementoValidator.validate(valid).any { it.field == "currencyCode" })
    }

    @Test
    fun negativePriceYieldsViolation() {
        val invalid = memento(priceMinorUnits = -1L)

        val violation = MementoValidator.validate(invalid).single { it.field == "priceMinorUnits" }

        assertEquals("priceMinorUnits", violation.field)
        assertTrue(violation.message.isNotBlank())
    }

    @Test
    fun zeroPriceIsAllowed() {
        val valid = memento(priceMinorUnits = 0L, currencyCode = "JPY")

        assertFalse(MementoValidator.validate(valid).any { it.field == "priceMinorUnits" })
        assertTrue(MementoValidator.isValid(valid))
    }

    @Test
    fun validatorReportsEveryViolationAtOnce() {
        val invalid = memento(
            title = "",
            place = null,
            media = emptyList(),
            currencyCode = "YENN",
            priceMinorUnits = -500L,
        )

        val fields = MementoValidator.validate(invalid).map { it.field }.toSet()

        assertEquals(setOf("title", "media", "currencyCode", "priceMinorUnits"), fields)
    }
}
