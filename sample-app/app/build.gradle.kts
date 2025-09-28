plugins {
    kotlin("jvm")
    id("com.github.topak.fast-dependency-analyser")
    application
}

application {
    mainClass.set("com.example.app.MainKt")
}

dependencies {
    // Used dependency - references classes from feature-home
    implementation(project(":feature-home"))

    // Used dependency - references classes from library-core
    implementation(project(":library-core"))

    // UNUSED dependency - this will be detected as unused by the plugin
    implementation(project(":library-data"))

    // UNUSED dependency - this will be detected as unused by the plugin
    implementation(project(":library-tracker"))
}