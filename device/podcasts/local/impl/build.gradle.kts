plugins {
    id("rolabox.android.library")
    id("rolabox.android.robolectric")
    id("rolabox.hilt")
    id("rolabox.room")
}

android {
    namespace = "com.eduardoflores.rolabox.device.podcasts.local.impl"
}

dependencies {
    implementation(project(":device:podcasts:api"))
    implementation(project(":device:podcasts:local:api"))
    // ArtworkImage, the pixels the artwork files are read as (ADR-031, rule 12).
    implementation(project(":device:library:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:util"))

    testImplementation(project(":device:podcasts:testing"))
    testImplementation(project(":common:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The database runs on the JVM, in memory and from its exported schemas (ADR-031).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
