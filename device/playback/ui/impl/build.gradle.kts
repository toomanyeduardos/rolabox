plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.device.playback.ui.impl"
}

dependencies {
    implementation(project(":device:playback:ui:api"))
    implementation(project(":device:host"))
    // The state of the screens (ADR-020: a part with screens reaches data through a data part's :api).
    implementation(project(":device:playback:api"))

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":common:testing"))
    // The ids of the items the tests queue.
    testImplementation(project(":device:library:api"))
    testImplementation(project(":device:playback:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
