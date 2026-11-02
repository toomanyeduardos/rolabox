plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:playback:api"))
    implementation(project(":device:playback:local:api"))
    implementation(project(":common:storage:api"))
    implementation(libs.javax.inject)
}
