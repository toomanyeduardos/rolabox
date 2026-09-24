plugins {
    id("rolabox.android.application")
    id("rolabox.android.compose")
    id("rolabox.hilt")
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
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:sync"))
    implementation(project(":feature:account"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
