package com.memento.domain.usecase

import com.memento.domain.model.Memento

/**
 * Case-insensitive substring search across a memento's title, reflection, tasting notes,
 * place metadata and tags. A blank query returns the input unchanged.
 */
class SearchMementosUseCase {
    operator fun invoke(mementos: List<Memento>, query: String): List<Memento> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return mementos
        return mementos.filter { memento -> memento.matches(needle) }
    }

    private fun Memento.matches(needle: String): Boolean {
        if (title.lowercase().contains(needle)) return true
        if (reflection.lowercase().contains(needle)) return true
        if (tastingNotes?.text?.lowercase()?.contains(needle) == true) return true
        if (place?.name?.lowercase()?.contains(needle) == true) return true
        if (place?.brand?.lowercase()?.contains(needle) == true) return true
        if (place?.city?.lowercase()?.contains(needle) == true) return true
        if (tags.any { it.value.lowercase().contains(needle) }) return true
        return false
    }
}
