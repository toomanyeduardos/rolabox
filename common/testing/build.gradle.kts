plugins {
    id("rolabox.android.library")
}

android {
    namespace = "com.eduardoflores.rolabox.common.testing"
}

dependencies {
    // FakeNonFatalReport implements it.
    api(project(":common:util"))
    api(libs.hilt.android.testing)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.runner)
    api(libs.junit)
}
