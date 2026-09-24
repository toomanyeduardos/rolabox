plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:model"))
    // Re-exports kotlinx.coroutines, whose Flow appears in the repository signatures.
    api(project(":core:common"))
    api(libs.arrow.core)
    implementation(libs.javax.inject)
}
