package com.eduardoflores.rolabox.device.podcasts.local.impl

import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONObject

/**
 * The schemas Room exported into `schemas/`, one file per version, which are in Git (ADR-024, rule 10). A migration
 * test makes a database exactly as a version was, with [create], puts the user's data in it, and opens it with the
 * current database and its migrations: Room refuses to open one whose tables aren't what the entities expect.
 */
internal object ExportedSchemas {
    // Unit tests run in the module's folder.
    private val folder = File("schemas/${PodcastsDatabase::class.java.name}")

    /** Every exported version, oldest first. */
    val versions: List<Int>
        get() = folder.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

    /** Makes the database at [file] as version [version] was exported, with no rows. */
    fun create(file: File, version: Int) {
        val database = JSONObject(File(folder, "$version.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = database.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").withTable(table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").withTable(table))
                }
            }
            val setup = database.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
        }
    }

    private fun String.withTable(table: String) = replace("\${TABLE_NAME}", table)
}
