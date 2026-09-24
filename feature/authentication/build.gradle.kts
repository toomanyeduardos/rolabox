plugins {
    id("rolabox.android.feature")
}

android {
    namespace = "com.eduardoflores.rolabox.feature.authentication"
}

dependencies {
    implementation(project(":core:auth"))
}
