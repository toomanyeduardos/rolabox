plugins {
    `kotlin-dsl`
}

group = "com.eduardoflores.rolabox.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompiler.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "rolabox.android.application"
            implementationClass = "RolaboxAndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "rolabox.android.library"
            implementationClass = "RolaboxAndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "rolabox.android.compose"
            implementationClass = "RolaboxAndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "rolabox.android.feature"
            implementationClass = "RolaboxAndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "rolabox.hilt"
            implementationClass = "RolaboxHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "rolabox.jvm.library"
            implementationClass = "RolaboxJvmLibraryConventionPlugin"
        }
    }
}
