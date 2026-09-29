plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures. Consumers declare the modules of StorageError
    // and SyncedValue themselves (ADR-003, rule 15).
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    implementation(project(":core:storage:api"))
    implementation(project(":core:sync:api"))
    testImplementation(libs.junit)
}
