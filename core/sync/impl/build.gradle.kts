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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(project(":core:auth:testing"))
    testImplementation(project(":core:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
