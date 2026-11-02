plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.playback.local.impl"
}

dependencies {
    implementation(project(":device:playback:local:api"))
    implementation(project(":device:playback:api"))
    implementation(project(":device:library:api"))
    implementation(project(":common:storage:api"))
    testImplementation(project(":common:storage:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
