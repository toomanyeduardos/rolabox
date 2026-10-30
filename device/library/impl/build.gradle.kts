plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.library.impl"
}

dependencies {
    implementation(project(":device:library:api"))
    // The debug stand-in library reads its files on the IO dispatcher.
    debugImplementation(project(":common:util"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
