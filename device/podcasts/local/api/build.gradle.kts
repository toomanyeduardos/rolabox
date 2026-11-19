plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures. Consumers declare the module of the podcasts' models
    // themselves (ADR-003, rule 15).
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    implementation(project(":device:podcasts:api"))
    // SyncedValue, which the follows are read and applied as (ADR-031, Syncing follows).
    implementation(project(":common:sync:api"))
    // ArtworkImage, the pixels an artwork is (ADR-031, rule 12).
    implementation(project(":device:library:api"))
}
