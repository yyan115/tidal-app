pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        // The mutation testing plugin is on Maven Central, but its marker for the plugin id is only on the Gradle plugin portal. Every job of the CI reads the plugin
        // list, so a portal that refuses the runner stopped jobs that never run a mutation test. This takes the plugin itself from the repositories above, which
        // the CI puts a copy of Maven Central in front of.
        eachPlugin {
            if (requested.id.id == "info.solidsoft.pitest") useModule("info.solidsoft.gradle.pitest:gradle-pitest-plugin:${requested.version}")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "OpenTune"
include(":core", ":app")
