plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow appears in the public signatures.
    api(libs.kotlinx.coroutines.core)
}
