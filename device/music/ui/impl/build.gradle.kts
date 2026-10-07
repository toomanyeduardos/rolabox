plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.device.music.ui.impl"
}

dependencies {
    implementation(project(":device:music:ui:api"))
    implementation(project(":device:host"))
    // The data of the screens (ADR-020: a part with screens reaches data through a data part's :api).
    implementation(project(":device:library:api"))

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // The read error the fake library is told to fail with.
    testImplementation(project(":common:storage:api"))
    testImplementation(project(":common:testing"))
    testImplementation(project(":device:library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
