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
    implementation(project(":device:library:network:api"))
    // Offline mode stops the download of covers (ADR-027, ADR-008 rule 6).
    implementation(project(":common:userdata:api"))
    implementation(project(":common:storage:api"))
    // The download of covers is WorkManager work (ADR-027).
    implementation(libs.androidx.work.runtime)
    // The scanner runs in the application scope, on the IO dispatcher (ADR-024).
    implementation(project(":common:util"))

    testImplementation(project(":device:library:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
