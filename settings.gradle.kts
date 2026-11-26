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
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }  // Kakao SDK
    }
}
rootProject.name = "NotificationCleaner"
include(":app")
includeBuild("core")  // pure Kotlin rules engine, also built on its own (gradle -p core)
