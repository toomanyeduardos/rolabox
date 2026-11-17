package com.eduardoflores.rolabox.device.podcasts.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    @Test
    fun accents_sortWithTheirLetter() {
        assertEquals("ecoute", sortKeyOf("Écoute"))
        assertEquals("cancion de la noche", sortKeyOf("Canción de la Noche"))
        assertEquals("uber alles", sortKeyOf("Über Alles"))
    }

    @Test
    fun anAccentedTitle_sortsBetweenItsNeighbours() {
        val titles = listOf("Field Notes", "Écoute", "Daily Dose of Design", "Zébra", "Ezra")

        assertEquals(
            listOf("Daily Dose of Design", "Écoute", "Ezra", "Field Notes", "Zébra"),
            titles.sortedBy(::sortKeyOf),
        )
    }

    @Test
    fun aLeadingThe_caseAndWhitespace_areIgnored() {
        assertEquals("daily", sortKeyOf("The Daily"))
        assertEquals("ezra klein show", sortKeyOf("  THE   Ezra  Klein Show "))
        assertEquals("theory of everything", sortKeyOf("Theory of Everything"))
    }
}
