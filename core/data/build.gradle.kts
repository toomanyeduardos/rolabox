plugins {
    id("rolabox.android.library")
}

android {
    namespace = "com.eduardoflores.rolabox.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
}
