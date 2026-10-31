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

        testInstrumentationRunner = "com.eduardoflores.rolabox.common.testing.RolaboxTestRunner"
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
    // Every :impl, so Hilt can assemble the graph (ADR-020). :app's code uses none of them.
    implementation(project(":auth:data:impl"))
    implementation(project(":auth:settings:impl"))
    implementation(project(":auth:ui:impl"))
    implementation(project(":common:storage:impl"))
    implementation(project(":common:sync:impl"))
    implementation(project(":common:userdata:impl"))
    implementation(project(":common:userdata:local:impl"))
    implementation(project(":common:userdata:network:impl"))
    implementation(project(":device:library:impl"))
    implementation(project(":device:library:local:impl"))
    implementation(project(":device:music:ui:impl"))
    implementation(project(":device:playback:impl"))
    implementation(project(":device:playback:ui:impl"))
    implementation(project(":device:settings:impl"))
    implementation(project(":device:ui:impl"))

    // What :app's own code uses.
    implementation(project(":auth:data:api"))
    implementation(project(":auth:ui:api"))
    implementation(project(":common:designsystem"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
    implementation(project(":common:userdata:api"))
    implementation(project(":common:util"))
    implementation(project(":device:ui:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation3.ui)
    testImplementation(project(":auth:data:testing"))
    testImplementation(project(":common:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":auth:data:testing"))
    androidTestImplementation(project(":common:sync:testing"))
    androidTestImplementation(project(":common:testing"))
    androidTestImplementation(project(":common:userdata:testing"))
    // The device's tests bring their own library, and check what the wheel asks of playback.
    androidTestImplementation(project(":device:library:testing"))
    // The navigation test reads the rows it expects from the tests' own library.
    androidTestImplementation(project(":device:library:api"))
    androidTestImplementation(project(":device:playback:testing"))
    // Tests may also name the :api of a part whose assembly they assert or whose fake they use (ADR-020, rule 13).
    // The back test loads items into the fake playback, and checks on it that nothing was sought.
    androidTestImplementation(project(":device:playback:api"))
    // The test of the assembled graph names the contract a section is contributed through (ADR-020, rule 20).
    androidTestImplementation(project(":device:settings:api"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
