plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Either appears in the public signatures.
    api(libs.arrow.core)
}
