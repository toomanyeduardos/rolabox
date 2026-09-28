plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow, Either, StorageError and SyncedValue appear in the public signatures.
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    api(project(":core:storage:api"))
    api(project(":core:sync:api"))
    testImplementation(libs.junit)
}
