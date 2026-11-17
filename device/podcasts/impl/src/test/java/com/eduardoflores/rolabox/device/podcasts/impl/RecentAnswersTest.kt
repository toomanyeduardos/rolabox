package com.eduardoflores.rolabox.device.podcasts.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentAnswersTest {
    private val answers = RecentAnswers<String, Int>(capacity = 2)

    @Test
    fun pastItsCapacity_theOneAskedForLongestAgo_isForgotten() {
        answers["a"] = 1
        answers["b"] = 2
        // Asking for "a" makes "b" the eldest.
        assertEquals(1, answers["a"])
        answers["c"] = 3

        assertNull(answers["b"])
        assertEquals(1, answers["a"])
        assertEquals(3, answers["c"])
    }
}
