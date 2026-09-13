package com.memento.domain.usecase

import com.memento.domain.model.CollectionId
import com.memento.domain.model.Memento
import kotlinx.datetime.Instant

/** Sort orders available to the timeline. */
enum class MementoSort {
    NEWEST_FIRST,
    OLDEST_FIRST,
    HIGHEST_RATED,
    TITLE,
}

/**
 * Declarative filter criteria for a list of mementos. All fields are optional; an empty filter
 * matches every memento.
 */
data class MementoFilter(
    val collectionId: CollectionId? = null,
    val tag: String? = null,
    val minRating: Int? = null,
    val brand: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val query: String? = null,
    val sort: MementoSort = MementoSort.NEWEST_FIRST,
)

/**
 * Pure, side-effect-free filtering over an in-memory list of mementos.
 */
class FilterMementosUseCase(
    private val search: SearchMementosUseCase = SearchMementosUseCase(),
) {
    operator fun invoke(
        mementos: List<Memento>,
        filter: MementoFilter = MementoFilter(),
    ): List<Memento> {
        var result = mementos

        filter.collectionId?.let { collectionId ->
            result = result.filter { collectionId in it.collectionIds }
        }
        filter.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            result = result.filter { memento ->
                memento.tags.any { it.value.equals(tag, ignoreCase = true) }
            }
        }
        filter.minRating?.let { minRating ->
            result = result.filter { (it.rating?.stars ?: 0) >= minRating }
        }
        filter.brand?.takeIf { it.isNotBlank() }?.let { brand ->
            result = result.filter { memento ->
                memento.place?.brand?.equals(brand, ignoreCase = true) == true
            }
        }
        filter.from?.let { from ->
            result = result.filter { it.occurredAt >= from }
        }
        filter.to?.let { to ->
            result = result.filter { it.occurredAt <= to }
        }
        filter.query?.takeIf { it.isNotBlank() }?.let { query ->
            result = search(result, query)
        }

        return applySort(result, filter.sort)
    }

    private fun applySort(mementos: List<Memento>, sort: MementoSort): List<Memento> = when (sort) {
        MementoSort.NEWEST_FIRST -> mementos.sortedByDescending { it.occurredAt }
        MementoSort.OLDEST_FIRST -> mementos.sortedBy { it.occurredAt }
        MementoSort.HIGHEST_RATED -> mementos.sortedByDescending { it.rating?.stars ?: Int.MIN_VALUE }
        MementoSort.TITLE -> mementos.sortedBy { it.title.lowercase() }
    }
}
