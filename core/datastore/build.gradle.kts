plugins {
    id("rolabox.android.library")
}

android {
    namespace = "com.eduardoflores.rolabox.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
}
