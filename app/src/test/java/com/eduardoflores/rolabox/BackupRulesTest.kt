package com.eduardoflores.rolabox

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * ADR-036 rules 1 to 3: the backup rules are one include-list, the same for cloud backup, device transfer and
 * Android 10 and 11; a database goes with its `-wal` and `-shm`; and every Room database and DataStore file in the
 * code is either included or in [LEFT_OUT], ADR-036's list of what is left out.
 */
class BackupRulesTest {
    @Test
    fun `the app's backup rules follow ADR-036`() {
        assertEquals(
            emptyList<String>(),
            problems(
                parse(File(EXTRACTION_RULES)),
                parse(File(BACKUP_RULES)),
                parse(File(MANIFEST)),
                storesInCode(File("..")),
                LEFT_OUT,
            ),
        )
    }

    @Test
    fun `rules that differ, exclude, split a database, or miss a store are reported`() {
        val extraction = parse(
            """
            <data-extraction-rules>
                <cloud-backup>
                    <include domain="database" path="a.db" />
                    <include domain="database" path="a.db-wal" />
                    <include domain="database" path="cache.db" />
                    <exclude domain="file" path="episodes/" />
                </cloud-backup>
                <device-transfer>
                    <include domain="database" path="a.db" />
                </device-transfer>
            </data-extraction-rules>
            """.trimIndent(),
        )
        val backup = parse(
            """
            <full-backup-content>
                <include domain="database" path="a.db" />
                <include domain="database" path="a.db-wal" />
                <include domain="database" path="cache.db" />
            </full-backup-content>
            """.trimIndent(),
        )
        val manifest = parse(
            """
            <manifest xmlns:android="$ANDROID_NS">
                <application android:allowBackup="true" android:fullBackupContent="@xml/backup_rules" />
            </manifest>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "The manifest doesn't name @xml/data_extraction_rules.",
                "<device-transfer> doesn't include what <cloud-backup> does.",
                "<cloud-backup> has an <exclude>: the rules are an include-list.",
                "database:a.db is included without database:a.db-shm.",
                "database:cache.db is included without database:cache.db-wal.",
                "database:cache.db is included without database:cache.db-shm.",
                "database:new.db has no decision: include it, or add it to ADR-036's list of what is left out.",
                "database:cache.db is included, but ADR-036 leaves it out.",
                "database:gone.db is left out, but no code names it any more.",
            ),
            problems(
                extraction,
                backup,
                manifest,
                storesInCode = setOf("database:a.db", "database:cache.db", "database:new.db"),
                leftOut = setOf("database:cache.db", "database:gone.db"),
            ),
        )
    }

    @Test
    fun `the stores are read from the database names and DataStore files of the main sources`() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            File(root, "a/src/main/java/Db.kt").write("""const val NAME = "things.db"""")
            File(root, "a/src/main/java/Store.kt").write("""context.preferencesDataStoreFile("settings")""")
            File(root, "a/src/test/java/TestDb.kt").write("""const val NAME = "test.db"""")
            File(root, "a/build/generated/Gen.kt").write("""const val NAME = "generated.db"""")

            assertEquals(
                setOf("database:things.db", "file:datastore/settings.preferences_pb"),
                storesInCode(root),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun problems(
        extraction: Document,
        backup: Document,
        manifest: Document,
        storesInCode: Set<String>,
        leftOut: Set<String>,
    ): List<String> {
        val sections = listOf(
            "<cloud-backup>" to extraction.elements("cloud-backup").singleOrNull(),
            "<device-transfer>" to extraction.elements("device-transfer").singleOrNull(),
            "<full-backup-content>" to backup.documentElement,
        )
        val included = sections.first().second?.paths("include").orEmpty()
        return manifestProblems(manifest) +
            includeListProblems(sections, included) +
            logFileProblems(included) +
            decisionProblems(included, storesInCode, leftOut)
    }

    private fun manifestProblems(manifest: Document): List<String> = buildList {
        val application = manifest.elements("application").single()
        if (application.getAttributeNS(ANDROID_NS, "allowBackup") != "true") add("The manifest doesn't allow backup.")
        if (application.getAttributeNS(ANDROID_NS, "dataExtractionRules") != "@xml/data_extraction_rules") {
            add("The manifest doesn't name @xml/data_extraction_rules.")
        }
        if (application.getAttributeNS(ANDROID_NS, "fullBackupContent") != "@xml/backup_rules") {
            add("The manifest doesn't name @xml/backup_rules.")
        }
    }

    // Rule 1: one include-list, in three places.
    private fun includeListProblems(sections: List<Pair<String, Element?>>, included: Set<String>): List<String> =
        buildList {
            sections.forEach { (name, section) ->
                when {
                    section == null -> add("$name is missing.")
                    section.paths("include") != included -> add("$name doesn't include what <cloud-backup> does.")
                }
            }
            sections.forEach { (name, section) ->
                if (section != null && section.paths("exclude").isNotEmpty()) {
                    add("$name has an <exclude>: the rules are an include-list.")
                }
            }
        }

    // Rule 2: a database with its log files.
    private fun logFileProblems(included: Set<String>): List<String> = buildList {
        included.filter { it.startsWith("database:") && it.endsWith(".db") }.forEach { database ->
            LOG_SUFFIXES.map { database + it }.filter { it !in included }.forEach {
                add("$database is included without $it.")
            }
        }
        included.filter { path -> LOG_SUFFIXES.any { path.endsWith(it) } }.forEach { log ->
            val database = log.dropLast(LOG_SUFFIXES.first().length)
            if (database !in included) add("$log is included without $database.")
        }
    }

    // Rule 3: every store has a decision.
    private fun decisionProblems(included: Set<String>, storesInCode: Set<String>, leftOut: Set<String>) = buildList {
        (storesInCode - included - leftOut).forEach {
            add("$it has no decision: include it, or add it to ADR-036's list of what is left out.")
        }
        (leftOut intersect included).forEach { add("$it is included, but ADR-036 leaves it out.") }
        (leftOut - storesInCode).forEach { add("$it is left out, but no code names it any more.") }
    }

    /** The Room databases (a `"….db"` literal) and DataStore files in the main sources under [root]. */
    private fun storesInCode(root: File): Set<String> = root.walkTopDown()
        .onEnter { it.name !in SKIPPED_FOLDERS }
        .filter { it.isFile && it.extension == "kt" && MAIN_SOURCES in it.invariantSeparatorsPath }
        .flatMap { file ->
            val text = file.readText()
            DATABASE_NAME.findAll(text).map { "database:${it.groupValues[1]}" } +
                DATASTORE_FILE.findAll(text).map { "file:datastore/${it.groupValues[1]}.preferences_pb" }
        }
        .toSet()

    private fun File.write(text: String) {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun parse(file: File): Document = builder().parse(file)

    private fun parse(xml: String): Document = builder().parse(xml.byteInputStream())

    private fun builder() = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()

    private fun Document.elements(name: String): List<Element> = getElementsByTagName(name).toElements()

    /** The `domain:path` of each of this section's [tag]s. */
    private fun Element.paths(tag: String): Set<String> =
        getElementsByTagName(tag).toElements().map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()

    private fun org.w3c.dom.NodeList.toElements(): List<Element> = List(length) { item(it) as Element }

    private companion object {
        // Unit tests run in the module's folder.
        const val EXTRACTION_RULES = "src/main/res/xml/data_extraction_rules.xml"
        const val BACKUP_RULES = "src/main/res/xml/backup_rules.xml"
        const val MANIFEST = "src/main/AndroidManifest.xml"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val MAIN_SOURCES = "/src/main/"

        val LOG_SUFFIXES = listOf("-wal", "-shm")
        val DATABASE_NAME = Regex(""""([\w.-]+\.db)"""")
        val DATASTORE_FILE = Regex("""preferencesDataStoreFile\(\s*"([\w.-]+)"\s*\)""")
        val SKIPPED_FOLDERS = setOf("build", ".git", ".gradle", ".claude", ".idea", "node_modules", "firebase")

        /** ADR-036's list of the stores in the code that are left out of the backup. */
        val LEFT_OUT = setOf("database:library-index.db")
    }
}
