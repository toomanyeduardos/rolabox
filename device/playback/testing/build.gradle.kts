plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:playback:api"))
    implementation(libs.javax.inject)
}
