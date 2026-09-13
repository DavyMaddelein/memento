package com.memento.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A 1..5 star rating. Enforced at construction so an invalid rating can never exist.
 */
@Serializable
@JvmInline
value class Rating(val stars: Int) {
    init {
        require(stars in MIN..MAX) { "Rating must be between $MIN and $MAX, was $stars" }
    }

    companion object {
        const val MIN = 1
        const val MAX = 5
    }
}
