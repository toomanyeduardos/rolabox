plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":core:auth:api"))
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
