plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":common:sync:api"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
