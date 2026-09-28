plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:auth:api"))
    implementation(libs.javax.inject)
}
