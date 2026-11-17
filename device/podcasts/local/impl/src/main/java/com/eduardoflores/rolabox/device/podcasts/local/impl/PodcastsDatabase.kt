package com.eduardoflores.rolabox.device.podcasts.local.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The podcasts' own database (ADR-024, rule 5, and ADR-031). Every table is the user's data: each change has a
 * migration in [MIGRATIONS], tested against the schema exported into `schemas/`.
 */
@Database(
    entities = [ShowEntity::class, EpisodeEntity::class, ProgressEntity::class, FollowEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class PodcastsDatabase : RoomDatabase() {
    abstract fun podcastsDao(): PodcastsDao

    abstract fun progressDao(): ProgressDao

    companion object {
        const val NAME = "podcasts.db"

        /** Every migration, from the first version to the last. */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
