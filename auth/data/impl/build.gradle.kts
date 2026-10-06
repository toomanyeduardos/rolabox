plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.auth.data.impl"

    testOptions {
        // Firebase's exception classes call Android helpers that the unit-test android.jar doesn't implement.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":auth:data:api"))
    implementation(project(":common:util"))
    // The use cases combine auth with the offline-mode choice, and auth tells sync who to sync for.
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
    implementation(project(":common:userdata:api"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(project(":auth:data:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
