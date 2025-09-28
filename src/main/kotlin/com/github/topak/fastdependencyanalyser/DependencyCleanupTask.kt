package com.github.topak.fastdependencyanalyser

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.LocalDateTime

private const val MAX_DEPENDENCIES_FOR_SEQUENTIAL = 3

abstract class DependencyCleanupTask : DefaultTask() {

  @InputDirectory
  val rootDir: File = project.rootDir

  @InputFile
  val dependencyReport: File = rootDir.resolve("dependency-report.txt")

  @Input
  @Optional
  val targetModule: String? = if (project.hasProperty("module")) project.property("module").toString() else null

  init {
    // Make this task depend on checkDependenciesClass
    dependsOn("checkDependencies")
  }

  data class CleanupResult(
    val dependency: String,
    val moduleName: String,
    val removed: Boolean,
    val reason: String,
    val buildSuccess: Boolean = false,
  )

  @TaskAction
  fun cleanupDependencies() {
    println("Starting dependency cleanup...")

    // Check if dependency report exists
    if (!dependencyReport.exists()) {
      println("Dependency report not found: ${dependencyReport.absolutePath}")
      println("The checkDependenciesClass task should have run first and generated this file.")
      return
    }

    // Parse unused dependencies from the report file
    val unusedDependencies = parseUnusedDependenciesFromReport()

    if (unusedDependencies.isEmpty()) {
      println("No unused dependencies found in report!")
      return
    }

    val results = mutableListOf<CleanupResult>()
    var totalRemoved = 0

    unusedDependencies.forEach { (moduleName, dependencies) ->
      println("\nProcessing module: $moduleName")

      val moduleResults = cleanupModuleDependencies(moduleName, dependencies)
      results.addAll(moduleResults)
      totalRemoved += moduleResults.count { it.removed }
    }

    // Final validation if any dependencies were removed
    if (totalRemoved > 0) {
      println("\nRunning final validation...")
      val buildValidator = BuildValidator(rootDir)
      val finalResult = buildValidator.validateFullBuild()

      if (!finalResult.success) {
        println("Final build validation failed!")
        println("Build output: ${finalResult.errorOutput}")

        println("You may need to manually review the changes made during cleanup")
      } else {
        println("Final build validation passed!")
      }
    }

    // Generate cleanup report
    generateCleanupReport(results, totalRemoved)
  }

  private fun parseUnusedDependenciesFromReport(): Map<String, List<String>> {
    val reportContent = dependencyReport.readText()
    val lines = reportContent.lines()
    val result = mutableMapOf<String, List<String>>()

    var currentModule: String = ""
    val currentDependencies = mutableListOf<String>()

    lines.forEach { line ->
      val trimmed = line.trim()

      when {
        // Check for module header (e.g., "feature:home")
        trimmed.startsWith("Module: ") -> {
          // Save previous module if exists
          if (currentModule.isNotEmpty() && currentDependencies.isNotEmpty()) {
            result[currentModule] = currentDependencies.toList()
            currentDependencies.clear()
          }
          // Extract module name
          currentModule = trimmed.removePrefix("Module: ").trim()
        }

        // Check for dependency line (e.g., "    - library.components.bottomsheetMenu")
        trimmed.startsWith("- ") -> {
          val dependency = trimmed.removePrefix("- ").trim()
          if (dependency.isNotEmpty()) {
            currentDependencies.add(dependency)
          }
        }

        // Skip other lines (headers, separators, etc.)
        else -> {
          // Do nothing
        }
      }
    }

    // Don't forget the last module
    if (currentModule.isNotEmpty() && currentDependencies.isNotEmpty()) {
      result[currentModule] = currentDependencies.toList()
    }

    println("Parsed ${result.size} modules with unused dependencies from report")
    result.forEach { (module, deps) ->
      println("  $module: ${deps.size} dependencies")
    }

    return result
  }

  private fun cleanupModuleDependencies(moduleName: String, dependencies: List<String>): List<CleanupResult> {
    val modulePath = moduleName.replace(':', '/')
    val buildFile = rootDir.resolve("$modulePath/build.gradle")
    val buildFileKts = rootDir.resolve("$modulePath/build.gradle.kts")

    val actualBuildFile = when {
      buildFile.exists() -> buildFile
      buildFileKts.exists() -> buildFileKts
      else -> {
        println("No build file found for module: $moduleName")
        return dependencies.map { CleanupResult(it, moduleName, false, "No build file found") }
      }
    }

    val buildFileEditor = BuildFileEditor(actualBuildFile)
    val buildValidator = BuildValidator(rootDir)

    println("Found ${dependencies.size} unused dependencies to remove")

    buildFileEditor.clearDuplicatedDependencies()

    return if (dependencies.size <= MAX_DEPENDENCIES_FOR_SEQUENTIAL) {
      println("Using sequential removal mode")
      sequentialRemoval(buildFileEditor, buildValidator, moduleName, dependencies)
    } else {
      println("Using fast binary search mode (default)")
      binarySearchRemoval(buildFileEditor, buildValidator, moduleName, dependencies)
    }
  }

  private fun sequentialRemoval(
    buildFileEditor: BuildFileEditor,
    buildValidator: BuildValidator,
    moduleName: String,
    dependencies: List<String>,
  ): List<CleanupResult> {
    val results = mutableListOf<CleanupResult>()

    dependencies.forEach { dependency ->
      println("\n  Processing dependency: $dependency")

      // Create backup
      if (!buildFileEditor.createBackup()) {
        results.add(CleanupResult(dependency, moduleName, false, "Failed to create backup"))
        return@forEach
      }

      // Try to remove the dependency
      if (!buildFileEditor.removeDependency(dependency)) {
        buildFileEditor.restoreFromBackup()
        results.add(CleanupResult(dependency, moduleName, false, "Failed to remove from build file"))
        return@forEach
      }

      // Validate the build
      println("    Validating build...")
      val buildResult = buildValidator.quickCompileCheck(moduleName)

      if (buildResult.success) {
        println("    Build validation passed - dependency removed")
        buildFileEditor.cleanupBackup()
        results.add(CleanupResult(dependency, moduleName, true, "Successfully removed", true))
      } else {
        println("    Build validation failed - restoring dependency")
        if (buildResult.errorOutput.isNotEmpty()) {
          val errorPreview = buildResult.errorOutput.take(200)
          println("    Error: $errorPreview${if (buildResult.errorOutput.length > 200) "..." else ""}")
        }
        buildFileEditor.restoreFromBackup()
        results.add(CleanupResult(dependency, moduleName, false, "Build validation failed", false))
      }
    }

    return results
  }

  private fun binarySearchRemoval(
    buildFileEditor: BuildFileEditor,
    buildValidator: BuildValidator,
    moduleName: String,
    dependencies: List<String>,
  ): List<CleanupResult> {
    if (dependencies.isEmpty()) return emptyList()
    if (dependencies.size == 1) {
      return sequentialRemoval(buildFileEditor, buildValidator, moduleName, dependencies)
    }

    println("  Testing removal of ${dependencies.size} dependencies...")

    // Create backup
    if (!buildFileEditor.createBackup()) {
      return dependencies.map { CleanupResult(it, moduleName, false, "Failed to create backup") }
    }

    // Try removing all dependencies at once
    val removeFailures = mutableListOf<String>()
    dependencies.forEach { dependency ->
      if (!buildFileEditor.removeDependency(dependency)) {
        removeFailures.add(dependency)
      }
    }

    if (removeFailures.isNotEmpty()) {
      buildFileEditor.restoreFromBackup()
      return removeFailures.map { CleanupResult(it, moduleName, false, "Failed to remove from build file") } +
          sequentialRemoval(
            buildFileEditor, buildValidator, moduleName,
            dependencies.filter { !removeFailures.contains(it) },
          )
    }

    // Test the build with all removed
    println("    Validating build with all dependencies removed...")
    val buildResult = buildValidator.quickCompileCheck(moduleName)

    if (buildResult.success) {
      // All can be safely removed!
      println("    All ${dependencies.size} dependencies successfully removed")
      buildFileEditor.cleanupBackup()
      return dependencies.map { CleanupResult(it, moduleName, true, "Successfully removed (batch)", true) }
    }

    // Some dependencies are needed, restore and use binary search
    buildFileEditor.restoreFromBackup()
    println("    Some dependencies are needed, splitting and retrying...")

    val mid = dependencies.size / 2
    val firstHalf = dependencies.take(mid)
    val secondHalf = dependencies.drop(mid)

    // Recursively process each half
    val firstResults = if (firstHalf.isNotEmpty()) {
      binarySearchRemoval(buildFileEditor, buildValidator, moduleName, firstHalf)
    } else emptyList()

    val secondResults = if (secondHalf.isNotEmpty()) {
      binarySearchRemoval(buildFileEditor, buildValidator, moduleName, secondHalf)
    } else emptyList()

    return firstResults + secondResults
  }

  private fun generateCleanupReport(results: List<CleanupResult>, totalRemoved: Int) {
    val reportFile = rootDir.resolve("dependency-cleanup-report.txt")
    val reportContent = StringBuilder()

    val header = "Dependency Cleanup Report"
    val separator = "=".repeat(header.length)

    println("\n$header")
    println(separator)

    reportContent.appendLine(header)
    reportContent.appendLine(separator)
    reportContent.appendLine()

    val timestamp = LocalDateTime.now().toString()
    val summaryLine = "Generated: $timestamp"
    println(summaryLine)
    reportContent.appendLine(summaryLine)

    val targetLine = if (targetModule != null) "Target: $targetModule" else "Target: All modules"
    println(targetLine)
    reportContent.appendLine(targetLine)

    val summary = "Summary: $totalRemoved dependencies removed, ${results.size - totalRemoved} failed"
    println(summary)
    reportContent.appendLine(summary)
    reportContent.appendLine()

    // Group results by outcome and module
    val removed = results.filter { it.removed }
    val failed = results.filter { !it.removed }

    if (removed.isNotEmpty()) {
      val removedHeader = "Successfully Removed (${removed.size}):"
      println(removedHeader)
      reportContent.appendLine(removedHeader)

      // Group removed dependencies by module
      val removedByModule = removed.groupBy { it.moduleName }
      removedByModule.forEach { (moduleName, moduleResults) ->
        val moduleHeader = "Module: $moduleName"
        println(moduleHeader)
        reportContent.appendLine(moduleHeader)
        moduleResults.forEach { result ->
          val line = "  - ${result.dependency}"
          println(line)
          reportContent.appendLine(line)
        }
      }
      reportContent.appendLine()
    }

    if (failed.isNotEmpty()) {
      val failedHeader = "Failed to Remove (${failed.size}):"
      println(failedHeader)
      reportContent.appendLine(failedHeader)

      // Group failed dependencies by module
      val failedByModule = failed.groupBy { it.moduleName }
      failedByModule.forEach { (moduleName, moduleResults) ->
        val moduleHeader = "Module: $moduleName"
        println(moduleHeader)
        reportContent.appendLine(moduleHeader)
        moduleResults.forEach { result ->
          val line = "  - ${result.dependency} (${result.reason})"
          println(line)
          reportContent.appendLine(line)
        }
      }
    }

    // Write report to file
    reportFile.writeText(reportContent.toString())
    println("\nCleanup report saved to: ${reportFile.absolutePath}")
  }
}