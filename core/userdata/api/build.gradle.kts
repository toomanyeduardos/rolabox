plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Re-exports kotlinx.coroutines, whose Flow appears in the repository signatures.
    api(project(":core:common"))
    // StorageError appears in the repository signatures.
    api(project(":core:storage:api"))
    api(libs.arrow.core)
}
