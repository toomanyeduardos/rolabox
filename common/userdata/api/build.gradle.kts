plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures. Consumers declare the modules of StorageError
    // and SyncedValue themselves (ADR-003, rule 15).
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
}
