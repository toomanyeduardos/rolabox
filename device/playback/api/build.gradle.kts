plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures.
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    // Consumers declare the module of SongId and LibraryError themselves (ADR-003, rule 15).
    implementation(project(":device:library:api"))
}
