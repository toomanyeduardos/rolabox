plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
    // The main menu's wiring to the screen stack is tested composed, on Robolectric.
    id("rolabox.android.robolectric")
}

android {
    namespace = "com.eduardoflores.rolabox.device.ui.impl"
}

dependencies {
    implementation(project(":device:ui:api"))
    implementation(project(":device:host"))
    // The device's own parts, whose exits it maps to keys as their parent.
    implementation(project(":device:music:ui:api"))
    implementation(project(":device:playback:ui:api"))
    // The playback buttons and the song click act on the playback state, and the ids in its models.
    implementation(project(":device:playback:api"))
    implementation(project(":device:library:api"))
    implementation(project(":device:settings:api"))
    // Which rows of the main menu are shown is a preference (ADR-020, rule 15, and ADR-011).
    implementation(project(":common:userdata:api"))
    implementation(project(":common:storage:api"))

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":common:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(project(":device:playback:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // The Espresso that Compose's test rule brings calls an InputManager method Robolectric's SDK no longer has.
    testImplementation(libs.androidx.espresso.core)
}
