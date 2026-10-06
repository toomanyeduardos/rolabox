plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":common:userdata:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
    implementation(libs.javax.inject)
}
