plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.playback.impl"
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:playback:api"))
    testImplementation(project(":common:storage:api"))
    testImplementation(project(":device:library:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
