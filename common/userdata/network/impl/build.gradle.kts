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
    // The one provider of Firestore, and the conversion of its exceptions (ADR-033).
    implementation(project(":common:firestore"))
    // Firestore is a dependency of :network:impl modules and :common:firestore only (ADR-033, rule 3).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(project(":common:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
