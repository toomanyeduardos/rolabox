plugins {
    id("rolabox.android.library")
    id("rolabox.android.robolectric")
    id("rolabox.hilt")
    id("rolabox.room")
}

android {
    namespace = "com.eduardoflores.rolabox.device.library.local.impl"
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:library:local:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:util"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The DAOs and the MediaStore query run on the JVM (ADR-024).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
