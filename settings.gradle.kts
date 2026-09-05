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
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Forge"

// Structure de SPEC.md §3 : deux applications (téléphone, montre) consommant en parallèle
// les mêmes modules de cœur. `wear` ne dépend jamais de `app`.
include(":app")
include(":wear")
include(":core-domain")
include(":core-data")
include(":core-ai")
include(":core-sync")
