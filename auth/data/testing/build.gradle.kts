plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":auth:data:api"))
    implementation(project(":common:storage:api"))
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
