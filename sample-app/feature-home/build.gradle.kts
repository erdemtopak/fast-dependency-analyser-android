plugins {
    kotlin("jvm")
}

dependencies {
    // Used dependency - references classes from library-core
    implementation(project(":library-core"))

    // UNUSED dependency - this will be detected as unused by the plugin
    implementation(project(":library-data"))

    // Used dependency - references classes from library-tracker
    implementation(project(":library-tracker"))
}