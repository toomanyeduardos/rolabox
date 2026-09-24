plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.auth"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
}
