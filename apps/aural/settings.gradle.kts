pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Aural"
include(":app", ":core", ":sports")
project(":core").projectDir = file("../../shared/audio-core")
project(":sports").projectDir = file("../../shared/sports-core")
