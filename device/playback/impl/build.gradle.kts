plugins {
    id("rolabox.android.library")
    id("rolabox.android.robolectric")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.playback.impl"

    defaultConfig {
        // The service is a Hilt entry point, so the instrumented test runs in Hilt's test application.
        testInstrumentationRunner = "com.eduardoflores.rolabox.common.testing.RolaboxTestRunner"
    }
}

dependencies {
    // The application scope the clock runs in.
    implementation(project(":common:util"))
    // The notification's small icon, a brand asset (ADR-015, rule 1).
    implementation(project(":common:designsystem"))
    implementation(platform(libs.androidx.compose.bom)) // required by the design system's Compose, with no version
    implementation(project(":device:library:api"))
    implementation(project(":device:playback:api"))
    // What is restored after the app is killed (ADR-023, rule 11).
    implementation(project(":device:playback:local:api"))
    implementation(project(":common:storage:api")) // required by its errors
    // The Shuffle and Repeat settings, which playback reads itself (ADR-028, rule 1).
    implementation(project(":common:userdata:api"))
    // Stores when the app goes to the background, and restores when it comes back.
    implementation(libs.androidx.lifecycle.process)
    // The engine, its session and the service (ADR-023). Only this module may depend on Media3 (rule 9).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // Media3 is called on the main thread, and connecting the controller answers with a ListenableFuture.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava) // required by media3

    testImplementation(project(":device:playback:testing"))
    testImplementation(project(":device:library:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // A real ExoPlayer on the JVM, with Media3's fake clock, playing library/02's fixtures (ADR-023).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)

    // The instrumented test plays one of library/02's fixtures through the service.
    androidTestImplementation(project(":common:testing"))
    androidTestImplementation(project(":device:library:testing"))
    androidTestImplementation(project(":device:playback:testing"))
    androidTestImplementation(project(":common:userdata:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
