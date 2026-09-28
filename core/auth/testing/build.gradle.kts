plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:auth:api"))
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
