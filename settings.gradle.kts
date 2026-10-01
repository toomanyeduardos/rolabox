pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Rolabox"

// A second time, outside pluginManagement: that one only shares build-logic's plugins, and this one
// lets `detektPlugins` resolve build-logic's detekt rules (ADR-017).
includeBuild("build-logic")
include(":app")

include(":core:common")
include(":core:designsystem")
include(":core:domain")
include(":core:testing")

include(":core:auth:api")
include(":core:auth:impl")
include(":core:auth:testing")
include(":core:auth:ui")

include(":core:storage:api")
include(":core:storage:impl")
include(":core:storage:testing")

include(":core:sync:api")
include(":core:sync:impl")
include(":core:sync:testing")

include(":core:userdata:api")
include(":core:userdata:impl")
include(":core:userdata:testing")

include(":feature:account")
include(":feature:settings")
