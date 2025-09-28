plugins {
    kotlin("jvm") version "1.9.10"
    `gradle-plugin-publish`
    `maven-publish`
    id("java-gradle-plugin")
}

group = "com.github.topak"
version = "1.0.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation(gradleTestKit())
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("fastDependencyAnalyser") {
            id = "com.github.topak.fast-dependency-analyser"
            implementationClass = "com.github.topak.fastdependencyanalyser.FastDependencyAnalyserPlugin"
            displayName = "Fast Dependency Analyser"
            description = "A Gradle plugin to find and remove unused dependencies quickly using bytecode analysis"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.topak"
            artifactId = "fast-dependency-analyser"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Fast Dependency Analyser")
                description.set("A Gradle plugin to find and remove unused dependencies quickly using bytecode analysis")
                url.set("https://github.com/topak/fast-dependency-analyser")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("topak")
                        name.set("Topak")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/topak/fast-dependency-analyser.git")
                    developerConnection.set("scm:git:ssh://github.com/topak/fast-dependency-analyser.git")
                    url.set("https://github.com/topak/fast-dependency-analyser")
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}