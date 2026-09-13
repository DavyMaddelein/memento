package com.memento.domain.model

import kotlinx.serialization.Serializable

/**
 * Where a keepsake was recorded. [name] is required; all other metadata is optional
 * because offline capture may only have raw coordinates.
 */
@Serializable
data class Place(
    val name: String,
    val brand: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val country: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Place name must not be blank" }
    }
}
