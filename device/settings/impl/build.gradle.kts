plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
    // The licences are parsed with Android's org.json, which runs on Robolectric.
    id("rolabox.android.robolectric")
}

android {
    namespace = "com.eduardoflores.rolabox.device.settings.impl"
}

dependencies {
    implementation(project(":device:settings:api"))
    implementation(project(":device:host"))
    // The settings are preferences, which :common:userdata stores (ADR-011), and About counts the
    // library's songs (ADR-020: a part with screens reaches data through a data part's :api).
    implementation(project(":common:storage:api"))
    implementation(project(":common:userdata:api"))
    implementation(project(":device:library:api"))
    // The licences are read on the IO dispatcher, and their errors converted with catchNamed (ADR-007).
    implementation(project(":common:util"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":common:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(project(":device:library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
