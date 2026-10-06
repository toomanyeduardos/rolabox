plugins {
    id("rolabox.android.screens")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.auth.ui.impl"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":auth:data:api"))
    implementation(project(":auth:ui:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:userdata:api"))

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Google sign-in through Credential Manager (ADR-009).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    testImplementation(project(":auth:data:testing"))
    testImplementation(project(":common:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
