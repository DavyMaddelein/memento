package com.memento.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MementoId(val value: String)

@Serializable
@JvmInline
value class CollectionId(val value: String)

@Serializable
@JvmInline
value class MediaId(val value: String)
