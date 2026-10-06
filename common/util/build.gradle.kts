plugins {
    id("rolabox.jvm.library")
    id("rolabox.hilt")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // catchNamed returns Either.
    api(libs.arrow.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
