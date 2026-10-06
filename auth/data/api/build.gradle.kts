plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures.
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    // SignInError wraps StorageError. Consumers that name it declare its module themselves (ADR-003, rule 15).
    implementation(project(":common:storage:api"))
    testImplementation(libs.junit)
}
