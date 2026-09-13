package com.memento.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Tag(val value: String) {
    init {
        require(value.isNotBlank()) { "Tag must not be blank" }
        require(value.length <= MAX_LENGTH) { "Tag must be at most $MAX_LENGTH characters, was ${value.length}" }
    }

    companion object {
        const val MAX_LENGTH = 50
    }
}
