plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Either appears in the public signatures. Consumers declare the module of the podcasts' models themselves
    // (ADR-003, rule 15).
    api(libs.arrow.core)
    implementation(project(":device:podcasts:api"))
    // RemoteError, which a sync of the follows can end in, and SyncedValue, which they sync as (ADR-022).
    implementation(project(":common:sync:api"))
    // Flow, for whether the phone is connected.
    implementation(libs.kotlinx.coroutines.core)
}
