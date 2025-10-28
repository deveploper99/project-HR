pluginManagement {
    repositories {
        google()         // ✅ এটা Firebase এর জন্য অবশ্যই দরকার
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()         // ✅ Firebase library এখান থেকে resolve হয়
        mavenCentral()
    }
}
rootProject.name = "aplay"
include(":app")
