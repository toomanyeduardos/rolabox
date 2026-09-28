plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.auth.impl"
}

dependencies {
    // AuthModule binds the :api interface, so :app's Hilt graph needs it on its classpath.
    api(project(":core:auth:api"))
    cloudImplementation(platform(libs.firebase.bom))
    cloudImplementation(libs.firebase.auth)
}
