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

    testImplementation(project(":device:podcasts:testing"))
    testImplementation(project(":common:storage:api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
