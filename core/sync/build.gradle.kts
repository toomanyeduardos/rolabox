plugins {
    id("rolabox.android.library")
}

android {
    namespace = "com.eduardoflores.rolabox.core.sync"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
}
