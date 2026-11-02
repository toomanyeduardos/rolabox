plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Either appears in the public signatures. Consumers declare the modules of StorageError and the playback's
    // models themselves (ADR-003, rule 15).
    api(libs.arrow.core)
    implementation(project(":common:storage:api"))
    implementation(project(":device:playback:api"))
}
