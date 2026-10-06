plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":common:storage:api"))
    implementation(libs.javax.inject)
}
