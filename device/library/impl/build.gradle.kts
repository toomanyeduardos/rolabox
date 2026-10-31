plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.library.impl"
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:library:local:api"))
    // The scanner runs in the application scope, on the IO dispatcher (ADR-024).
    implementation(project(":common:util"))

    testImplementation(project(":common:storage:api"))
    testImplementation(project(":device:library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
