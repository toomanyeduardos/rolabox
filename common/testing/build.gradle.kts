plugins {
    id("rolabox.android.library")
}

android {
    namespace = "com.eduardoflores.rolabox.common.testing"
}

dependencies {
    api(libs.hilt.android.testing)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.runner)
    api(libs.junit)
}
