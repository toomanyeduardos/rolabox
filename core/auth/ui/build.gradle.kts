plugins {
    id("rolabox.android.ui")
}

android {
    namespace = "com.eduardoflores.rolabox.core.auth.ui"
}

dependencies {
    implementation(project(":core:auth:api"))
    implementation(project(":core:domain"))
    implementation(project(":core:userdata:api"))

    // Google sign-in through Credential Manager (ADR-009).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    testImplementation(project(":core:auth:testing"))
    testImplementation(project(":core:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
