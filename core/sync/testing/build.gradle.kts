plugins {
    id("rolabox.jvm.library")
}

dependencies {
    api(project(":core:sync:api"))
    implementation(libs.javax.inject)
}
