pluginManagement {
    repositories {
        google()         // Google plugins
        mavenCentral()   // Firebase + Google API libraries
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()         // Firebase / Google libraries
        mavenCentral()   // Gmail API library
        maven { url = uri("https://maven.google.com") } // Optional
    }
}

rootProject.name = "aplay"
include(":app")
