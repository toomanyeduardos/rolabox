plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":core:sync:api"))
    implementation(libs.javax.inject)
}
