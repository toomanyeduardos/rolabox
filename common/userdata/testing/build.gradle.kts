plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":common:userdata:api"))
    implementation(project(":common:userdata:local:api"))
    implementation(project(":common:userdata:network:api"))
    implementation(project(":common:storage:api"))
    implementation(project(":common:sync:api"))
    implementation(libs.javax.inject)
}
