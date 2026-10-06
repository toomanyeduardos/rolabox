package com.eduardoflores.rolabox.common.sync.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.util.catchNamed
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Lazy
import java.util.Date
import javax.inject.Inject
import kotlin.enums.EnumEntries
import kotlinx.coroutines.tasks.await

/**
 * Stores the preferences in `users/{uid}/settings/preferences` (ADR-011), one map per field:
 * `{ value: <enum name>, updatedAt: <timestamp> }`. The field names and values are checked by
 * `firebase/firestore.rules`, so they change only together with the rules.
 *
 * Firestore is created on the first sync, never before, so offline mode never creates it (ADR-008
 * rule 8).
 */
internal class FirestoreRemotePreferences @Inject constructor(private val firestore: Lazy<FirebaseFirestore>) :
    RemotePreferences {
    override suspend fun merge(
        userId: String,
        merge: (remote: SyncedPreferences) -> SyncedPreferences,
    ): Either<RemoteError, SyncedPreferences> = catchNamed(Throwable::asRemoteError) {
        val db = firestore.get()
        val document = db.collection(USERS).document(userId).collection(SETTINGS).document(PREFERENCES)
        db.runTransaction { transaction ->
            val remote = transaction.get(document).toSyncedPreferences()
            val merged = merge(remote)
            // Only the fields that changed are written. A field this version can't read (a value
            // added by a newer version) is left alone unless this device's change is newer.
            val changes = DarkThemeConfigField.changes(remote.darkThemeConfig, merged.darkThemeConfig) +
                AccentColorField.changes(remote.accentColor, merged.accentColor)
            if (changes.isNotEmpty()) transaction.set(document, changes, SetOptions.merge())
            merged
        }.await()
    }

    private companion object {
        const val USERS = "users"
        const val SETTINGS = "settings"
        const val PREFERENCES = "preferences"
    }
}

private class RemoteField<T : Enum<T>>(val name: String, private val entries: EnumEntries<T>, private val default: T) {
    fun read(snapshot: DocumentSnapshot): SyncedValue<T>? {
        val field = snapshot.get(name) as? Map<*, *>
        val value = field?.get(VALUE) as? String
        val updatedAt = field?.get(UPDATED_AT) as? Timestamp
        // A value from a newer version reads as the default, the same as in local storage.
        return if (value == null || updatedAt == null) {
            null
        } else {
            SyncedValue(entries.firstOrNull { it.name == value } ?: default, SyncTimestamp(updatedAt.toDate().time))
        }
    }

    fun changes(remote: SyncedValue<T>?, merged: SyncedValue<T>?): Map<String, Any> =
        if (merged == null || merged == remote) {
            emptyMap()
        } else {
            mapOf(
                name to mapOf(VALUE to merged.value.name, UPDATED_AT to Timestamp(Date(merged.updatedAt.epochMillis))),
            )
        }

    private companion object {
        const val VALUE = "value"
        const val UPDATED_AT = "updatedAt"
    }
}

private val DarkThemeConfigField =
    RemoteField("darkThemeConfig", DarkThemeConfig.entries, DarkThemeConfig.FOLLOW_SYSTEM)
private val AccentColorField = RemoteField("accentColor", AccentColor.entries, AccentColor.BLUE)

private fun DocumentSnapshot.toSyncedPreferences() = SyncedPreferences(
    darkThemeConfig = DarkThemeConfigField.read(this),
    accentColor = AccentColorField.read(this),
)
