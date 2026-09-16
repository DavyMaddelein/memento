package com.memento.portability

import com.memento.domain.model.referencedMediaIds
import com.memento.storage.contract.AssetStore
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.flow.first

/**
 * Reclaims asset bytes that are no longer referenced by any memento.
 *
 * Deleting a memento is intentionally non-destructive at the storage layer so the timeline's
 * "Undo" can restore it with its photos intact. That leaves the removed memento's (and any
 * abandoned capture's) bytes behind. Running this sweep on a fresh app start — after deletion and
 * capture flows have settled — collects those orphans without racing the undo affordance.
 */
class MediaGarbageCollector(
    private val mementoRepository: MementoRepository,
    private val assetStore: AssetStore,
) {
    /** Deletes every stored media id not referenced by a memento; returns how many were removed. */
    suspend fun collect(): Int {
        val referenced = mementoRepository.observeAllMementos().first().referencedMediaIds()
        var removed = 0
        for (mediaId in assetStore.listMedia()) {
            if (mediaId in referenced) continue
            if (assetStore.deleteMedia(mediaId).isSuccess) removed++
        }
        return removed
    }
}
