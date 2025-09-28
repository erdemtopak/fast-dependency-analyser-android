package com.github.topak.fastdependencyanalyser

import org.gradle.api.Plugin
import org.gradle.api.Project

class FastDependencyAnalyserPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register the checkDependencies task
        project.tasks.register("checkDependencies", DependencyCheckerTask::class.java) { task ->
            task.description = "Analyzes project dependencies and identifies unused ones"
            task.group = "verification"
        }

        // Register the cleanupDependencies task
        project.tasks.register("cleanupDependencies", DependencyCleanupTask::class.java) { task ->
            task.description = "Automatically removes unused dependencies with build validation"
            task.group = "verification"
        }
    }
}