package com.memento.portability

import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import kotlinx.datetime.Instant

internal fun simpleMemento(
    id: String,
    occurredAt: String,
    title: String = id,
): Memento = Memento(
    id = MementoId(id),
    title = title,
    occurredAt = Instant.parse(occurredAt),
    createdAt = Instant.parse(occurredAt),
    updatedAt = Instant.parse(occurredAt),
)
