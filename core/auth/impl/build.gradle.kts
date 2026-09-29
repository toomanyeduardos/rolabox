plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.auth.impl"

    testOptions {
        // Firebase's exception classes call Android helpers that the unit-test android.jar doesn't implement.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:auth:api"))
    implementation(project(":core:common"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
