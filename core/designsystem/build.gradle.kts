plugins {
    id("rolabox.android.library")
    id("rolabox.android.compose")
}

android {
    namespace = "com.eduardoflores.rolabox.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
}
