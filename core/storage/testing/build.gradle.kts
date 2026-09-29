plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":core:storage:api"))
    implementation(libs.javax.inject)
}
