plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.datastore"
}

dependencies {
    api(project(":core:model"))
    // asDataStoreError() returns a domain error type.
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
}
