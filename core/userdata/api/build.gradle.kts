plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow, Either and StorageError appear in the public signatures.
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    api(project(":core:storage:api"))
}
