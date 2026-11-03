plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.library.network.impl"
}

dependencies {
    implementation(project(":device:library:network:api"))
    implementation(project(":common:util"))
    implementation(libs.arrow.core)
    // HTTP and JSON are dependencies of :network:impl modules only (ADR-027, rule 7).
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
