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

include(":auth:data:api")
include(":auth:data:impl")
include(":auth:data:testing")
include(":auth:settings:impl")
include(":auth:ui:api")
include(":auth:ui:impl")

include(":common:designsystem")
include(":common:testing")
include(":common:util")

include(":common:storage:api")
include(":common:storage:impl")
include(":common:storage:testing")

include(":common:sync:api")
include(":common:sync:impl")
include(":common:sync:testing")

include(":common:userdata:api")
include(":common:userdata:impl")
include(":common:userdata:testing")

include(":device:host")
include(":device:library:api")
include(":device:library:impl")
include(":device:library:testing")

include(":device:settings:api")
include(":device:settings:impl")
include(":device:ui:api")
include(":device:ui:impl")
