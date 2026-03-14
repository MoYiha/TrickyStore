pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "CleveresTricky"
// include(":module")  // Temporarily disabled due to AGP plugin issues
include(":service")
// include(":stub")  // Temporarily disabled due to AGP plugin issues
// include(":encryptor-app")  // Temporarily disabled due to AGP plugin issues
