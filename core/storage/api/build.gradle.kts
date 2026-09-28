plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Re-exports kotlinx.coroutines, whose Flow appears in the PreferencesStore signatures.
    api(project(":core:common"))
    api(libs.arrow.core)
}
