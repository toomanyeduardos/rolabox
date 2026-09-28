plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:storage:api"))
    implementation(libs.javax.inject)
}
