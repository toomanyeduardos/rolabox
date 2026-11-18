plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.device.podcasts.ui.impl"
}

dependencies {
    implementation(project(":device:podcasts:ui:api"))
    implementation(project(":device:host"))
    // The data of the screens (ADR-020: a part with screens reaches data through a data part's :api).
    implementation(project(":device:podcasts:api"))
    // Playing an episode (ADR-031, The layout).
    implementation(project(":device:playback:api"))

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // The read error the fake repository is told to fail with.
    testImplementation(project(":common:storage:api"))
    testImplementation(project(":common:testing"))
    testImplementation(project(":device:podcasts:testing"))
    testImplementation(project(":device:playback:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
