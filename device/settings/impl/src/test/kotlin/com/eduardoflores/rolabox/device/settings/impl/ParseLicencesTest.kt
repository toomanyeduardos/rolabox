package com.eduardoflores.rolabox.device.settings.impl

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json is Android's: the JVM's stubs throw, so it runs on Robolectric.
@RunWith(RobolectricTestRunner::class)
class ParseLicencesTest {
    @Test
    fun readsEachLibrary_withItsLicences_sortedByName() {
        val json = """
            {
              "libraries": [
                {"uniqueId": "com.google.re2j:re2j", "artifactVersion": "1.8", "name": "RE2/J", "licenses": ["go"]},
                {"uniqueId": "androidx.activity:activity", "artifactVersion": "1.13.0", "name": "Activity",
                 "licenses": ["Apache-2.0"]}
              ],
              "licenses": {
                "Apache-2.0": {"name": "The Apache License, Version 2.0", "url": "https://apache.org", "content": "Apache text"},
                "go": {"name": "Go License", "url": "https://golang.org/LICENSE", "content": "Go text"}
              }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                Licence(
                    id = "androidx.activity:activity",
                    name = "Activity",
                    version = "1.13.0",
                    licenceName = "The Apache License, Version 2.0",
                    text = "Apache text",
                ),
                Licence("com.google.re2j:re2j", "RE2/J", "1.8", "Go License", "Go text"),
            ),
            parseLicences(json),
        )
    }

    @Test
    fun aLicenceWithoutText_showsItsUrl_andSeveralLicencesAreJoined() {
        val json = """
            {
              "libraries": [{"uniqueId": "a:b", "name": "Both", "licenses": ["ASDKL", "MIT"]}],
              "licenses": {
                "ASDKL": {"name": "Android Software Development Kit License", "url": "https://developer.android.com/studio/terms.html"},
                "MIT": {"name": "MIT license", "url": "https://spdx.org/licenses/MIT.html", "content": "MIT text"}
              }
            }
        """.trimIndent()

        assertEquals(
            Licence(
                id = "a:b",
                name = "Both",
                version = null,
                licenceName = "Android Software Development Kit License, MIT license",
                text = "https://developer.android.com/studio/terms.html\n\nMIT text",
            ),
            parseLicences(json).single(),
        )
    }

    @Test
    fun aLibraryWithoutAName_isShownByItsId_andAnUnknownLicenceByItsKey() {
        val json = """{"libraries": [{"uniqueId": "a:b", "licenses": ["Custom"]}], "licenses": {}}"""

        assertEquals(Licence("a:b", "a:b", null, "Custom", null), parseLicences(json).single())
    }

    // The fonts are listed by hand in config/aboutlibraries/libraries/, with no version, each with its own OFL text.
    @Test
    fun aFont_hasNoVersion_andShowsItsOwnOflText() {
        val json = """
            {
              "libraries": [{"uniqueId": "font:hanken-grotesk", "name": "Hanken Grotesk",
                             "licenses": ["OFL-1.1-hanken-grotesk"]}],
              "licenses": {"OFL-1.1-hanken-grotesk": {"name": "SIL Open Font License 1.1",
                           "url": "https://openfontlicense.org", "content": "Copyright 2021 The Authors"}}
            }
        """.trimIndent()

        assertEquals(
            Licence(
                id = "font:hanken-grotesk",
                name = "Hanken Grotesk",
                version = null,
                licenceName = "SIL Open Font License 1.1",
                text = "Copyright 2021 The Authors",
            ),
            parseLicences(json).single(),
        )
    }

    @Test(expected = JSONException::class)
    fun aDamagedList_throws_soTheReaderSaysItIsUnavailable() {
        parseLicences("{\"libraries\": ")
    }
}
