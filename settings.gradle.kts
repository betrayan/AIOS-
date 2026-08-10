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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // libs.versions.toml is auto-discovered by Gradle 8.1+ from gradle/libs.versions.toml
}

rootProject.name = "BuddyAIOS"

// ── App ──────────────────────────────────────────────────────────────────────
include(":app")

// ── Core Modules ─────────────────────────────────────────────────────────────
include(":core:common")
include(":core:domain")
include(":core:database")
include(":core:network")
include(":core:security")
include(":core:ai")
include(":core:data")
include(":core:ui")
include(":core:analytics")

// ── Feature Modules ───────────────────────────────────────────────────────────
include(":feature:chat")
include(":feature:home")
include(":feature:memory")
include(":feature:settings")
include(":feature:onboarding")

// ── Workers ───────────────────────────────────────────────────────────────────
include(":workers")
