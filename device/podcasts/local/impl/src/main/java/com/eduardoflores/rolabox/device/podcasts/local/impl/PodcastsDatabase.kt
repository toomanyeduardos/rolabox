package com.eduardoflores.rolabox.device.podcasts.local.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The podcasts' own database (ADR-024, rule 5, and ADR-031). Every table is the user's data: each change has a
 * migration in [MIGRATIONS], tested against the schema exported into `schemas/`.
 */
@Database(
    entities = [
        ShowEntity::class,
        EpisodeEntity::class,
        ProgressEntity::class,
        FollowEntity::class,
        EpisodeOrderEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class PodcastsDatabase : RoomDatabase() {
    abstract fun podcastsDao(): PodcastsDao

    abstract fun progressDao(): ProgressDao

    companion object {
        const val NAME = "podcasts.db"

        /** Every migration, from the first version to the last. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}

/** podcasts/09's record of when an episode left its feed (ADR-031, The database). Every row stays, as it was. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `episodes` ADD COLUMN `leftFeedAt` INTEGER")
    }
}

/**
 * ADR-043's order of a show's episodes: the feed's `itunes:type` on each show, unknown until the feed is read again,
 * and the user's choices, none yet. Every row stays, as it was.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `shows` ADD COLUMN `kind` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episodeOrders` (`directoryId` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`directoryId`))",
        )
    }
}
