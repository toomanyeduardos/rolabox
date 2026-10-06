plugins {
    `kotlin-dsl`
}

group = "com.eduardoflores.rolabox.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompiler.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.googleServices.gradlePlugin)
    implementation(libs.paparazzi.gradlePlugin)

    testImplementation(libs.junit)
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
        register("androidApplicationFirebase") {
            id = "rolabox.android.application.firebase"
            implementationClass = "RolaboxAndroidApplicationFirebaseConventionPlugin"
        }
        register("androidCompose") {
            id = "rolabox.android.compose"
            implementationClass = "RolaboxAndroidComposeConventionPlugin"
        }
        register("androidScreens") {
            id = "rolabox.android.screens"
            implementationClass = "RolaboxAndroidScreensConventionPlugin"
        }
        register("jvmCompose") {
            id = "rolabox.jvm.compose"
            implementationClass = "RolaboxJvmComposeConventionPlugin"
        }
        register("navigation") {
            id = "rolabox.navigation"
            implementationClass = "RolaboxNavigationConventionPlugin"
        }
        register("hilt") {
            id = "rolabox.hilt"
            implementationClass = "RolaboxHiltConventionPlugin"
        }
        register("moduleGraph") {
            id = "rolabox.module.graph"
            implementationClass = "RolaboxModuleGraphConventionPlugin"
        }
        register("ktlint") {
            id = "rolabox.ktlint"
            implementationClass = "RolaboxKtlintConventionPlugin"
        }
        register("detekt") {
            id = "rolabox.detekt"
            implementationClass = "RolaboxDetektConventionPlugin"
        }
        register("androidPaparazzi") {
            id = "rolabox.android.paparazzi"
            implementationClass = "RolaboxAndroidPaparazziConventionPlugin"
        }
        register("jvmLibrary") {
            id = "rolabox.jvm.library"
            implementationClass = "RolaboxJvmLibraryConventionPlugin"
        }
    }
}
