pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://shoalapps.github.io/shoal-common/") {
            content { includeGroup("io.github.shoalapps.common") }
        }
        maven("https://jitpack.io")
    }
}

rootProject.name = "Tube"
include(":app")
