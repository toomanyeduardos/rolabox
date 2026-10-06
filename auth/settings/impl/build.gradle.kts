plugins {
    id("rolabox.android.screens")
}

android {
    namespace = "com.eduardoflores.rolabox.auth.settings.impl"
}

dependencies {
    // The one place where auth depends on the device (ADR-020, rule 3).
    implementation(project(":device:settings:api"))
    implementation(project(":auth:ui:api"))

    testImplementation(libs.junit)
}
