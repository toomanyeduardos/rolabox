package com.eduardoflores.rolabox

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * ADR-034 rule 1: plain HTTP is allowed by default, for podcast hosts, and forbidden for the domains of every service
 * Rolabox talks to, with their subdomains. [SERVICE_DOMAINS] is ADR-034's table: a new service is added there, in
 * the config, and here, in one change (rule 2).
 */
class NetworkSecurityConfigTest {
    @Test
    fun `the app's config and manifest allow plain HTTP only outside the services`() {
        assertEquals(
            emptyList<String>(),
            problems(parse(File(CONFIG)), parse(File(MANIFEST)), SERVICE_DOMAINS),
        )
    }

    @Test
    fun `a config that misses a service, lets one use HTTP, or blocks HTTP for podcasts is reported`() {
        val config = parse(
            """
            <network-security-config>
                <base-config cleartextTrafficPermitted="false" />
                <domain-config cleartextTrafficPermitted="false">
                    <domain includeSubdomains="true">googleapis.com</domain>
                    <domain>apple.com</domain>
                </domain-config>
                <domain-config cleartextTrafficPermitted="true">
                    <domain includeSubdomains="true">musicbrainz.org</domain>
                    <domain includeSubdomains="true">example.com</domain>
                </domain-config>
            </network-security-config>
            """.trimIndent(),
        )
        val manifest = parse(
            """
            <manifest xmlns:android="$ANDROID_NS">
                <application android:usesCleartextTraffic="true" />
            </manifest>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "The manifest doesn't name @xml/network_security_config.",
                "The manifest sets usesCleartextTraffic, which the config replaces.",
                "The base config doesn't allow plain HTTP, so podcast hosts can't use it.",
                "apple.com doesn't include its subdomains.",
                "musicbrainz.org allows plain HTTP.",
                "example.com is HTTPS-only config for a domain no service uses.",
                "coverartarchive.org isn't HTTPS-only.",
            ),
            problems(config, manifest, setOf("googleapis.com", "apple.com", "musicbrainz.org", "coverartarchive.org")),
        )
    }

    private fun problems(config: Document, manifest: Document, services: Set<String>): List<String> = buildList {
        val application = manifest.elements("application").single()
        if (application.getAttributeNS(ANDROID_NS, "networkSecurityConfig") != "@xml/network_security_config") {
            add("The manifest doesn't name @xml/network_security_config.")
        }
        if (application.hasAttributeNS(ANDROID_NS, "usesCleartextTraffic")) {
            add("The manifest sets usesCleartextTraffic, which the config replaces.")
        }

        val base = config.elements("base-config").singleOrNull()
        if (base?.getAttribute("cleartextTrafficPermitted") != "true") {
            add("The base config doesn't allow plain HTTP, so podcast hosts can't use it.")
        }

        val listed = config.elements("domain").associateBy { it.textContent.trim() }
        listed.forEach { (domain, element) ->
            when {
                domain !in services -> add("$domain is HTTPS-only config for a domain no service uses.")

                element.getAttribute("includeSubdomains") != "true" -> add("$domain doesn't include its subdomains.")

                (element.parentNode as Element).getAttribute("cleartextTrafficPermitted") != "false" ->
                    add("$domain allows plain HTTP.")
            }
        }
        (services - listed.keys).forEach { add("$it isn't HTTPS-only.") }
    }

    private fun parse(file: File): Document = builder().parse(file)

    private fun parse(xml: String): Document = builder().parse(xml.byteInputStream())

    private fun builder() = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()

    private fun Document.elements(name: String): List<Element> =
        getElementsByTagName(name).let { nodes -> List(nodes.length) { nodes.item(it) as Element } }

    private companion object {
        // Unit tests run in the module's folder.
        const val CONFIG = "src/main/res/xml/network_security_config.xml"
        const val MANIFEST = "src/main/AndroidManifest.xml"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        /** ADR-034's table of service domains. */
        val SERVICE_DOMAINS = setOf(
            "googleapis.com",
            "google.com",
            "crashlytics.com",
            "apple.com",
            "musicbrainz.org",
            "coverartarchive.org",
            "archive.org",
        )
    }
}
