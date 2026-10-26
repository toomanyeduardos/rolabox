plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.sync.impl"
}

dependencies {
    implementation(project(":common:sync:api"))
    implementation(project(":common:util"))
    implementation(project(":common:storage:api"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    testImplementation(project(":common:sync:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
