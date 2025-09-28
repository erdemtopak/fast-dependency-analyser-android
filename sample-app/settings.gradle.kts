rootProject.name = "sample-app"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

include(":app")
include(":feature-home")
include(":library-core")
include(":library-data")
include(":library-tracker")