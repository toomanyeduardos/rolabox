plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.auth.impl"
}

dependencies {
    implementation(project(":core:auth:api"))
    implementation(project(":core:common"))
    cloudImplementation(platform(libs.firebase.bom))
    cloudImplementation(libs.firebase.auth)
    // Awaits Firebase's Tasks. It depends on Play services, so it stays in cloud (ADR-008 rule 3).
    cloudImplementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
