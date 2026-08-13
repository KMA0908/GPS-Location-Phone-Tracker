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
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
        maven { url = uri("https://android-sdk.is.com/") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
        maven {
            url = uri("https://maven.pkg.github.com/ngoxuanhungbk/ls-leansoft-publishing-sdk")
            credentials {
                username = providers.gradleProperty("GITHUB_USERNAME").orNull
                    ?: System.getenv("GITHUB_USERNAME") ?: ""
                password = providers.gradleProperty("GITHUB_TOKEN").orNull
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        maven { url = uri("https://artifacts.applovin.com/android") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
        maven { url = uri("https://android-sdk.is.com/") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
        maven {
            url = uri("https://maven.pkg.github.com/ngoxuanhungbk/ls-leansoft-publishing-sdk")
            credentials {
                username = providers.gradleProperty("GITHUB_USERNAME").orNull
                    ?: System.getenv("GITHUB_USERNAME") ?: ""
                password = providers.gradleProperty("GITHUB_TOKEN").orNull
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        maven { url = uri("https://artifacts.applovin.com/android") }
    }
}

rootProject.name = "GPS Location Phone Tracker"
include(":app")
