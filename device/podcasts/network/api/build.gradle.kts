plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Either appears in the public signatures. Consumers declare the module of the podcasts' models themselves
    // (ADR-003, rule 15).
    api(libs.arrow.core)
    implementation(project(":device:podcasts:api"))
}
