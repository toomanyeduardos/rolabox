plugins {
    id("rolabox.jvm.library")
}

dependencies {
    // Flow and Either appear in the public signatures.
    api(libs.kotlinx.coroutines.core)
    api(libs.arrow.core)
    testImplementation(libs.junit)
}
