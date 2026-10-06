package com.eduardoflores.rolabox.common.storage.impl.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.impl.DataStorePreferencesStore
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    internal abstract fun bindsPreferencesStore(store: DataStorePreferencesStore): PreferencesStore

    companion object {
        // The file name is what's on users' devices, so it doesn't change.
        @Provides
        @Singleton
        fun providesPreferencesDataStore(
            @ApplicationContext context: Context,
            @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
            @ApplicationScope scope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        ) {
            context.preferencesDataStoreFile("user_preferences")
        }
    }
}
