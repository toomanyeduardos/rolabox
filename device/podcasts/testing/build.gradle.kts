plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":device:podcasts:api"))
    implementation(project(":device:podcasts:local:api"))
    implementation(project(":device:podcasts:network:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
    implementation(project(":device:library:api"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
