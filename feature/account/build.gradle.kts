plugins {
    id("rolabox.android.feature")
}

android {
    namespace = "com.eduardoflores.rolabox.feature.account"
}

dependencies {
    implementation(project(":core:auth"))
}
