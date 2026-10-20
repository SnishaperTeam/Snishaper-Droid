import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        // 华为云 Maven 镜像（本机实测可同时代理 Google Maven、Maven Central、
        // Gradle Plugin Portal 的插件 marker；下载速度显著优于直连）。官方源作为兜底。
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 华为云 Maven 镜像优先；AGP 9.4.1 / AndroidX / Compose / Kotlin 2.4.21
        // 均已实测存在。官方源兜底，CI（海外 runner）也无需改动。
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
    }
}

rootProject.name = "Snishaper"
include(":app")
