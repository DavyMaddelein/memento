package com.memento.domain.validation

import com.memento.domain.model.Memento

/** A single, non-fatal rule violation discovered while validating an aggregate. */
data class ValidationViolation(
    val field: String,
    val message: String,
)

/**
 * Outcome-oriented validation of the [Memento] aggregate. Returns every violation found rather
 * than throwing, so callers (forms, importers) can surface them all at once.
 */
object MementoValidator {
    fun validate(memento: Memento): List<ValidationViolation> = buildList {
        if (memento.title.isBlank() && memento.place == null) {
            add(
                ValidationViolation(
                    field = "title",
                    message = "A keepsake needs a title or a named place",
                ),
            )
        }
        if (memento.media.isEmpty()) {
            add(ValidationViolation(field = "media", message = "At least one photo is required"))
        }
        if (memento.currencyCode != null && memento.currencyCode.length != 3) {
            add(
                ValidationViolation(
                    field = "currencyCode",
                    message = "currencyCode must be a 3-letter ISO code",
                ),
            )
        }
        if (memento.priceMinorUnits != null && memento.priceMinorUnits < 0) {
            add(ValidationViolation(field = "priceMinorUnits", message = "Price must not be negative"))
        }
    }

    fun isValid(memento: Memento): Boolean = validate(memento).isEmpty()
}
