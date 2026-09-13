package com.memento.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RandomIdTest {

    @Test
    fun idIncludesPrefixWhenProvided() {
        val id = randomId("memento")

        assertTrue(id.startsWith("memento-"))
        assertTrue(id.length > "memento-".length)
    }

    @Test
    fun idHasNoDashWhenPrefixIsEmpty() {
        val id = randomId()

        assertFalse(id.contains("-"))
        assertEquals(32, id.length)
    }

    @Test
    fun generatedIdsAreUnique() {
        val ids = List(1000) { randomId("m") }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun idIsHexEncoded() {
        val suffix = randomId("x").removePrefix("x-")

        assertTrue(suffix.all { it in "0123456789abcdef" }, "Unexpected id characters: $suffix")
    }
}
