plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.common.crashreporting.impl"
}

dependencies {
    implementation(project(":common:util"))
    // The only module that depends on Crashlytics (ADR-030 rule 2).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit)
}
