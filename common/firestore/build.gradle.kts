plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.firestore"

    testOptions {
        // Firebase's exception classes call Android helpers that the unit-test android.jar doesn't implement.
        unitTests.isReturnDefaultValues = true
    }
}

// Provides Firestore once, and turns its exceptions into RemoteError, for every :network:impl (ADR-033).
dependencies {
    // RemoteError is in the public signature of the conversion. Its users declare :common:sync:api themselves.
    implementation(project(":common:sync:api"))
    implementation(project(":common:util"))
    // Firestore is a dependency of :network:impl modules and this one only (ADR-033, rule 3).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    testImplementation(project(":common:testing"))
    testImplementation(libs.junit)
}
