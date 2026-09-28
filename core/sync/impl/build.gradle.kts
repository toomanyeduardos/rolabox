plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.sync.impl"
}

dependencies {
    implementation(project(":core:sync:api"))
}
