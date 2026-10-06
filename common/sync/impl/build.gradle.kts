plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.sync.impl"
}

dependencies {
    implementation(project(":common:sync:api"))
    implementation(project(":common:util"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:userdata:api"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(project(":common:sync:testing"))
    testImplementation(project(":common:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
