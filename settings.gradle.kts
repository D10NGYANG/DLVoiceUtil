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
        jcenter()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository")
    }
}
rootProject.name = "DLVoiceUtil"
include("app", "library")
