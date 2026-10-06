plugins {
    id("rolabox.android.library")
    id("rolabox.android.compose")
    id("rolabox.navigation")
}

android {
    namespace = "com.eduardoflores.rolabox.device.host"
}

dependencies {
    implementation(project(":common:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)

    testImplementation(libs.junit)
}
