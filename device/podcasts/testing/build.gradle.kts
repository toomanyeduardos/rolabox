plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":device:podcasts:api"))
    implementation(project(":device:podcasts:local:api"))
    implementation(project(":common:storage:api"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
