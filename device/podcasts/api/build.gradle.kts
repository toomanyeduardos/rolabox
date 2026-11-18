plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures.
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    // Consumers declare the module of StorageError themselves (ADR-003, rule 15).
    implementation(project(":common:storage:api"))
    // ArtworkImage, the pixels an artwork crosses an :api as (ADR-026, rule 1; ADR-031, rule 12).
    implementation(project(":device:library:api"))
}
