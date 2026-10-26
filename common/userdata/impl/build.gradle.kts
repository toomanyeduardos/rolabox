plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.userdata.impl"
}

dependencies {
    implementation(project(":common:userdata:api"))
    implementation(project(":common:userdata:local:api"))
    implementation(project(":common:userdata:network:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(project(":common:sync:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
