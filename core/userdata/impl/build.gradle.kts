plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.userdata.impl"
}

dependencies {
    // UserDataModule binds the :api interface, so :app's Hilt graph needs it on its classpath.
    api(project(":core:userdata:api"))
    implementation(project(":core:storage:api"))
    testImplementation(project(":core:storage:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
