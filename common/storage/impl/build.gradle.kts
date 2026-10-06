plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.storage.impl"
}

dependencies {
    implementation(project(":common:storage:api"))
    implementation(project(":common:util"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
