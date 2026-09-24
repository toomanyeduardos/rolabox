package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryProductFlavor

const val BACKEND_DIMENSION = "backend"

/** ADR-008: which remote backend a build talks to. */
enum class BackendFlavor(val isDefault: Boolean) {
    /** No remote backend: no Firebase, account features hidden. Builds from a fresh clone. */
    OFFLINE(isDefault = true),

    /** Firebase enabled. Needs a google-services.json that isn't checked in. */
    CLOUD(isDefault = false),
    ;

    val flavorName: String = name.lowercase()
}

/** Every Android module gets the same dimension, so variant names (and task names) match across the build. */
internal fun CommonExtension.configureBackendFlavors() {
    flavorDimensions += BACKEND_DIMENSION
    BackendFlavor.entries.forEach { flavor ->
        productFlavors.register(flavor.flavorName) {
            dimension = BACKEND_DIMENSION
            when (this) {
                is ApplicationProductFlavor -> isDefault = flavor.isDefault
                is LibraryProductFlavor -> isDefault = flavor.isDefault
            }
        }
    }
}

// Covers cloudImplementation, cloudApi and per-variant forms (testCloudImplementation, cloudDebugImplementation, …).
internal fun String.isCloudConfiguration() = startsWith(BackendFlavor.CLOUD.flavorName) ||
    contains(BackendFlavor.CLOUD.flavorName.replaceFirstChar(Char::uppercaseChar))
