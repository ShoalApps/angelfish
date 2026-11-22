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

// Working on shoal-common alongside this app: put `shoal.common.dir=../shoal-common` in
// local.properties (never committed) and the build uses that checkout instead of the published
// library. Without it, nothing changes.
val shoalCommonDir = file("local.properties").takeIf { it.exists() }
    ?.readLines()?.firstOrNull { it.startsWith("shoal.common.dir=") }?.substringAfter('=')?.trim()
if (!shoalCommonDir.isNullOrEmpty() && file(shoalCommonDir).isDirectory) {
    includeBuild(shoalCommonDir) {
        dependencySubstitution {
            substitute(module("io.github.shoalapps.common:shoal-ui")).using(project(":ui"))
            substitute(module("io.github.shoalapps.common:shoal-expressive")).using(project(":expressive"))
        }
    }
}
