plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.testing"
}

dependencies {
    api(project(":core:model"))
    api(libs.hilt.android.testing)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.runner)
    api(libs.junit)
    api(project(":core:auth"))
    api(project(":core:common"))
    api(project(":core:data"))
    api(project(":core:sync"))
}
