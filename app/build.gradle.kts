plugins {
    id("rolabox.android.application")
    id("rolabox.android.compose")
}

android {
    namespace = "com.eduardoflores.rolabox"

    defaultConfig {
        applicationId = "com.eduardoflores.rolabox"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":core:designsystem"))
    implementation(project(":core:sync"))
    implementation(project(":feature:account"))
    implementation(project(":feature:authentication"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
