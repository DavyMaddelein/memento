package com.memento.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A pointer to a binary asset. The bytes themselves live in an [com.memento.domain.model.AssetStore]
 * (or platform storage); this is only the reference.
 */
@Serializable
data class MediaReference(
    val id: MediaId,
    val uri: String,
    val mimeType: String,
    val capturedAt: Instant,
) {
    init {
        require(uri.isNotBlank()) { "MediaReference uri must not be blank" }
        require(mimeType.isNotBlank()) { "MediaReference mimeType must not be blank" }
    }
}
