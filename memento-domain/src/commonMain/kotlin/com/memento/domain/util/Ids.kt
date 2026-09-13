package com.memento.domain.util

import kotlin.random.Random

/**
 * Deterministic-length, collision-resistant identifier generator that works on every target
 * (including wasm) without platform UUID APIs.
 */
fun randomId(prefix: String = ""): String {
    val hi = Random.nextLong().toULong().toString(16).padStart(16, '0')
    val lo = Random.nextLong().toULong().toString(16).padStart(16, '0')
    val value = "$hi$lo"
    return if (prefix.isEmpty()) value else "$prefix-$value"
}
