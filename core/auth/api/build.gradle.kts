plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Re-exports kotlinx.coroutines, whose Flow appears in the repository signatures.
    api(project(":core:common"))
}
