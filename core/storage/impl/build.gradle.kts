plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.storage.impl"
}

dependencies {
    // StorageModule binds the :api interface, so :app's Hilt graph needs it on its classpath.
    api(project(":core:storage:api"))
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
