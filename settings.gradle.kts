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
        maven { url = uri("https://maven.cafebazaar.ir") }
        maven { url = uri("https://cafebazaar.github.io/Poolakey/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CutStock"
include(":app")
