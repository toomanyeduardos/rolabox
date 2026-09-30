plugins {
    id("rolabox.android.ui")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.core.auth.ui"
}

dependencies {
    implementation(project(":core:auth:api"))

    // Google sign-in through Credential Manager (ADR-009).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    testImplementation(libs.junit)
}
