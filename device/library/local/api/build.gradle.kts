plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures. Consumers declare the module of the library's models
    // themselves (ADR-003, rule 15).
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    implementation(project(":device:library:api"))
}
