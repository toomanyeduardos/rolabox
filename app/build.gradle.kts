plugins {
    id("rolabox.android.application")
    id("rolabox.android.application.firebase")
    id("rolabox.android.compose")
    id("rolabox.hilt")
    id("rolabox.navigation")
}

android {
    namespace = "com.eduardoflores.rolabox"

    defaultConfig {
        applicationId = "com.eduardoflores.rolabox"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.eduardoflores.rolabox.core.testing.RolaboxTestRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:auth:impl"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:storage:impl"))
    implementation(project(":core:sync:impl"))
    implementation(project(":core:userdata:impl"))
    implementation(project(":core:auth:api"))
    implementation(project(":core:auth:ui"))
    implementation(project(":core:storage:api"))
    implementation(project(":core:sync:api"))
    implementation(project(":core:userdata:api"))
    implementation(project(":feature:account"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation3.ui)
    testImplementation(project(":core:auth:testing"))
    testImplementation(project(":core:storage:api"))
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:userdata:testing"))
    testImplementation(libs.junit)
    androidTestImplementation(project(":core:auth:api"))
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:auth:testing"))
    androidTestImplementation(project(":core:sync:testing"))
    androidTestImplementation(project(":core:userdata:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
