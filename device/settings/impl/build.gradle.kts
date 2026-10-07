plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.device.settings.impl"
}

dependencies {
    implementation(project(":device:settings:api"))
    // The theme Settings holds of its own (ADR-020: a part with screens reaches data through a data part's :api).
    implementation(project(":common:storage:api"))
    implementation(project(":common:userdata:api"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":common:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
