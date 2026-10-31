plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
    id("rolabox.room")
}

android {
    namespace = "com.eduardoflores.rolabox.device.library.local.impl"

    testOptions {
        // Robolectric reads the module's merged manifest and resources.
        unitTests.isIncludeAndroidResources = true
        // Robolectric reads the internals of a FileDescriptor, which the JDK no longer exports.
        unitTests.all { it.jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:library:local:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:util"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The DAOs and the MediaStore query run on the JVM (ADR-024).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
