plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":common:storage:api"))
    implementation(libs.javax.inject)
}
