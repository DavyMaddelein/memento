pluginManagement {
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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "memento-kmp"

include(":memento-domain")
include(":memento-storage-contract")
include(":memento-storage-memory")
include(":memento-storage-sqlite")
include(":memento-platform-contract")
include(":memento-platform-android")
include(":memento-platform-web")
include(":memento-presentation")
include(":memento-ui-compose")
include(":memento-data-portability")
include(":memento-app-android")
include(":memento-app-web")
