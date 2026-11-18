plugins {
    id("rolabox.android.library")
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
    // The artwork download is WorkManager work (ADR-031, Artwork).
    implementation(libs.androidx.work.runtime)

    testImplementation(project(":device:podcasts:testing"))
    testImplementation(project(":device:playback:testing"))
    testImplementation(project(":device:library:testing"))
    testImplementation(project(":common:storage:api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
