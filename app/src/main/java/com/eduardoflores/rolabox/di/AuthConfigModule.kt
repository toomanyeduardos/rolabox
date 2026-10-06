package com.eduardoflores.rolabox.di

import android.content.Context
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.auth.ui.api.SignInConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Configuration of the auth UI that only `:app` knows (ADR-009, rule 4). */
@Module
@InstallIn(SingletonComponent::class)
internal object AuthConfigModule {
    // The google-services plugin generates the resource from the `client_type` 3 entry of
    // google-services.json, whether that is the developer's own file or the placeholder.
    @Provides
    fun providesSignInConfig(@ApplicationContext context: Context): SignInConfig =
        SignInConfig(googleWebClientId = context.getString(R.string.default_web_client_id))
}
