plugins {
    id("rolabox.android.library")
    id("rolabox.android.robolectric")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.podcasts.impl"
}

dependencies {
    implementation(project(":device:podcasts:api"))
    implementation(project(":device:podcasts:local:api"))
    implementation(project(":device:podcasts:network:api"))
    // The state of each episode's download, which is playback's (ADR-031, The layout).
    implementation(project(":device:playback:api"))
    // The Online Artwork setting, which stops the artwork download too, and ArtworkImage (ADR-031, Artwork).
    implementation(project(":device:library:api"))
    // The artwork download and the follow check are WorkManager work (ADR-031, Artwork and Follow checks).
    implementation(libs.androidx.work.runtime)
    // The follow check is requested at process start, and again when the app comes back to the front (ADR-025).
    implementation(project(":common:util"))
    implementation(libs.androidx.lifecycle.process)

    testImplementation(project(":device:podcasts:testing"))
    testImplementation(project(":device:playback:testing"))
    testImplementation(project(":device:library:testing"))
    testImplementation(project(":common:storage:api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // That the follow check is enqueued as WorkManager work, with its constraint (podcasts/09).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
