plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":core:auth:api"))
    implementation(project(":core:userdata:api"))
    implementation(libs.javax.inject)
    testImplementation(project(":core:auth:testing"))
    testImplementation(project(":core:userdata:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
