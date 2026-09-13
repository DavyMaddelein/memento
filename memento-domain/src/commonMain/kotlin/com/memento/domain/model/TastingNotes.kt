package com.memento.domain.model

import kotlinx.serialization.Serializable

/**
 * Free-form personal impressions plus structured flavour tags.
 */
@Serializable
data class TastingNotes(
    val text: String = "",
    val flavorTags: List<String> = emptyList(),
) {
    init {
        require(text.length <= MAX_TEXT_LENGTH) {
            "TastingNotes text must be at most $MAX_TEXT_LENGTH characters, was ${text.length}"
        }
    }

    companion object {
        const val MAX_TEXT_LENGTH = 5000
    }
}
