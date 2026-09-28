plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.sync.impl"
}

dependencies {
    implementation(project(":core:sync:api"))
    implementation(project(":core:auth:api"))
    implementation(project(":core:common"))
    implementation(project(":core:storage:api"))
    implementation(project(":core:userdata:api"))
    // Only the cloud flavor syncs, so the offline app gets none of this (ADR-008, ADR-011).
    cloudImplementation(libs.androidx.lifecycle.process)
    cloudImplementation(libs.androidx.work.runtime)
    cloudImplementation(platform(libs.firebase.bom))
    cloudImplementation(libs.firebase.firestore)
    // Awaits Firebase's Tasks. It depends on Play services, so it stays in cloud (ADR-008 rule 3).
    cloudImplementation(libs.kotlinx.coroutines.play.services)
    testImplementation(project(":core:auth:testing"))
    testImplementation(project(":core:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
