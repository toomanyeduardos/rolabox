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
include(":app")

include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:designsystem")
include(":core:data")
include(":core:datastore")
include(":core:auth")
include(":core:sync")
include(":core:testing")

include(":feature:account")
include(":feature:settings")
