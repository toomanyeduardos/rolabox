plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.userdata.network.impl"
}

dependencies {
    implementation(project(":common:userdata:network:api"))
    implementation(project(":common:userdata:api"))
    implementation(project(":common:sync:api"))
    implementation(project(":common:util"))
    // Firestore is a dependency of :network:impl modules only (ADR-022, rule 3).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
