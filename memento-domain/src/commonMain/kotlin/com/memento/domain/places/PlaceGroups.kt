package com.memento.domain.places

import com.memento.domain.model.Memento
import kotlinx.datetime.Instant

/**
 * Mementos recorded at the same place, projected for a "Places" overview.
 *
 * [mementos] is always sorted newest-first ([Memento.occurredAt] descending).
 */
data class PlaceGroup(
    val key: String,
    val title: String,
    val subtitle: String?,
    val mementos: List<Memento>,
) {
    val count: Int get() = mementos.size

    /** Mean of the available [com.memento.domain.model.Rating] stars, or null when none are rated. */
    val averageRating: Double?
        get() {
            val ratings = mementos.mapNotNull { it.rating?.stars }
            return if (ratings.isEmpty()) null else ratings.average()
        }

    /** Timestamp of the newest memento in the group (the first element), or null when empty. */
    val latestOccurredAt: Instant? get() = mementos.firstOrNull()?.occurredAt
}

internal const val UNKNOWN_PLACE_KEY = "__unknown__"
private const val UNKNOWN_PLACE_TITLE = "Unknown location"

private class GroupAccumulator(
    val title: String,
    val keyedOnName: Boolean,
) {
    val mementos = mutableListOf<Memento>()
    val countries = mutableListOf<String>()
    val cities = mutableListOf<String>()
}

/**
 * Groups mementos by their [com.memento.domain.model.Place].
 *
 * The grouping key is the trimmed, lowercased city; when the city is blank it falls back to the
 * trimmed, lowercased place name. Mementos without either (or without a place) collect into a
 * single trailing "unknown" group.
 *
 * Groups are ordered by [PlaceGroup.count] descending, then [PlaceGroup.title] ascending, with the
 * unknown group always last. Within a group mementos are newest-first. The function is pure and
 * does not mutate its receiver.
 */
fun List<Memento>.groupMementosByPlace(): List<PlaceGroup> {
    val accumulators = linkedMapOf<String, GroupAccumulator>()

    for (memento in this) {
        val city = memento.place?.city?.trim().orEmpty()
        val name = memento.place?.name?.trim().orEmpty()

        val key: String
        val title: String
        val keyedOnName: Boolean
        when {
            city.isNotEmpty() -> {
                key = city.lowercase()
                title = city
                keyedOnName = false
            }
            name.isNotEmpty() -> {
                key = name.lowercase()
                title = name
                keyedOnName = true
            }
            else -> {
                key = UNKNOWN_PLACE_KEY
                title = UNKNOWN_PLACE_TITLE
                keyedOnName = false
            }
        }

        val accumulator = accumulators.getOrPut(key) { GroupAccumulator(title, keyedOnName) }
        accumulator.mementos += memento

        val country = memento.place?.country?.trim().orEmpty()
        if (country.isNotEmpty() && accumulator.countries.none { it.equals(country, ignoreCase = true) }) {
            accumulator.countries += country
        }
        if (keyedOnName && city.isNotEmpty() && accumulator.cities.none { it.equals(city, ignoreCase = true) }) {
            accumulator.cities += city
        }
    }

    val (unknown, known) = accumulators.entries.partition { it.key == UNKNOWN_PLACE_KEY }

    val knownGroups = known
        .map { (key, accumulator) -> accumulator.toPlaceGroup(key) }
        .sortedWith(compareByDescending<PlaceGroup> { it.count }.thenBy { it.title })

    val unknownGroups = unknown.map { (key, accumulator) -> accumulator.toPlaceGroup(key) }

    return knownGroups + unknownGroups
}

private fun GroupAccumulator.toPlaceGroup(key: String): PlaceGroup {
    val subtitleParts = buildList {
        addAll(countries)
        if (keyedOnName) addAll(cities)
    }
    return PlaceGroup(
        key = key,
        title = title,
        subtitle = subtitleParts.takeIf { it.isNotEmpty() }?.joinToString(", "),
        mementos = mementos.sortedByDescending { it.occurredAt },
    )
}
