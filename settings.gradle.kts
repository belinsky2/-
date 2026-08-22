pluginManagement {
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
}

rootProject.name = "punchline"

// :core:model — чистый Kotlin/JVM. Он собирается и тестируется без Android SDK,
// поэтому доменную логику можно проверять там, где Google Maven недоступен:
//   PUNCHLINE_JVM_ONLY=1 ./gradlew :core:model:test
include(":core:model")

if (System.getenv("PUNCHLINE_JVM_ONLY") != "1") {
    include(":core:data")
    include(":app")
}
