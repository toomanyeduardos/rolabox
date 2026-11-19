plugins {
    id("rolabox.android.library")
    id("rolabox.android.compose")
    id("rolabox.navigation")
}

android {
    namespace = "com.eduardoflores.rolabox.device.host"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":common:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)

    testImplementation(libs.junit)

    // The keyboard shows and hides over a field, in an activity that is edge to edge (ADR-032).
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.activity.compose)
}
