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

include(":core:common")
include(":core:designsystem")
include(":core:testing")

include(":core:auth:api")
include(":core:auth:impl")
include(":core:auth:testing")

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
