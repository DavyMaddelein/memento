package com.memento.presentation

import com.memento.domain.model.MediaReference
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Tag
import com.memento.domain.util.randomId
import com.memento.platform.contract.fakes.sampleMediaReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Instant

/** A ViewModel scope bound to the test scheduler so `advanceUntilIdle()` drives it. */
fun TestScope.newViewModelScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

fun testMemento(
    id: String = randomId("memento"),
    title: String = "",
    tags: List<String> = emptyList(),
    occurredAt: Instant = Instant.fromEpochMilliseconds(0),
    brand: String? = null,
    city: String? = null,
    media: List<MediaReference> = listOf(sampleMediaReference("default")),
): Memento = Memento(
    id = MementoId(id),
    title = title,
    place = brand?.let { Place(name = it, brand = it, city = city) },
    media = media,
    tags = tags.map { Tag(it) },
    occurredAt = occurredAt,
    createdAt = occurredAt,
    updatedAt = occurredAt,
)
