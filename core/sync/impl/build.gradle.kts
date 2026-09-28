plugins {
    id("rolabox.android.library")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.core.sync.impl"
}

dependencies {
    // SyncModule binds the :api interface, so :app's Hilt graph needs it on its classpath.
    api(project(":core:sync:api"))
}
