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

/**
 * Every media id referenced by any of these mementos. Used to find orphaned assets: bytes whose id
 * is absent from this set are not referenced by any keepsake and can be safely reclaimed.
 */
fun List<Memento>.referencedMediaIds(): Set<MediaId> =
    flatMapTo(mutableSetOf()) { memento -> memento.media.map { it.id } }
