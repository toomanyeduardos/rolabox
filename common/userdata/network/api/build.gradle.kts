plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Either appears in the public signatures. Consumers declare the modules of RemoteError and
    // SyncedPreferences themselves (ADR-003, rule 15).
    api(libs.arrow.core)
    implementation(project(":common:sync:api"))
    implementation(project(":common:userdata:api"))
}
