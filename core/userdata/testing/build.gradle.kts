plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:userdata:api"))
    implementation(project(":core:storage:api"))
    implementation(project(":core:sync:api"))
    implementation(libs.javax.inject)
}
