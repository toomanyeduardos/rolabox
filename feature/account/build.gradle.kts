plugins {
    id("rolabox.android.feature")
    id("rolabox.navigation")
}

android {
    namespace = "com.eduardoflores.rolabox.feature.account"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:auth:api"))
    implementation(project(":core:auth:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:storage:api"))
    implementation(project(":core:userdata:api"))

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":core:auth:testing"))
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
