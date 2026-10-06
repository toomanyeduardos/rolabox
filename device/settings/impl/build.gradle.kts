plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.device.settings.impl"
}

dependencies {
    implementation(project(":device:settings:api"))

    testImplementation(libs.junit)
}
