plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:userdata:api"))
    implementation(project(":core:storage:api"))
    implementation(libs.javax.inject)
}
