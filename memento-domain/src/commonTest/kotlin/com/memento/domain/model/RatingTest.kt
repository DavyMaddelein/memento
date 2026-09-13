package com.memento.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RatingTest {

    @Test
    fun minimumRatingIsValid() {
        assertEquals(1, Rating(1).stars)
        assertEquals(1, Rating.MIN)
    }

    @Test
    fun maximumRatingIsValid() {
        assertEquals(5, Rating(5).stars)
        assertEquals(5, Rating.MAX)
    }

    @Test
    fun middleRatingsAreValid() {
        assertEquals(3, Rating(3).stars)
    }

    @Test
    fun zeroThrows() {
        assertFailsWith<IllegalArgumentException> { Rating(0) }
    }

    @Test
    fun sixThrows() {
        assertFailsWith<IllegalArgumentException> { Rating(6) }
    }

    @Test
    fun negativeThrows() {
        assertFailsWith<IllegalArgumentException> { Rating(-1) }
    }
}
