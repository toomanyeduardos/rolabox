plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
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
    implementation(project(":device:settings:api"))

    testImplementation(libs.junit)
}
