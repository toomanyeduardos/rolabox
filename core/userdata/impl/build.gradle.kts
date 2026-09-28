plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.userdata.impl"
}

dependencies {
    implementation(project(":core:userdata:api"))
    implementation(project(":core:storage:api"))
    implementation(project(":core:sync:api"))
    testImplementation(project(":core:storage:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
