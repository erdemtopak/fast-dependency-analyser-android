package com.github.topak.fastdependencyanalyser

import java.io.File
import java.io.IOException

class BuildFileEditor(private val buildFile: File) {

  private val backupFile: File = File("${buildFile.absolutePath}.backup")

  fun clearDuplicatedDependencies() {
    if (!buildFile.exists()) {
      println("Build file does not exist: ${buildFile.absolutePath}")
      return
    }

    val content = buildFile.readText()
    val lines = content.lines().toMutableList()
    val seenDependencies = mutableSetOf<String>()
    val cleanedLines = mutableListOf<String>()

    for (line in lines) {
      val trimmedLine = line.trim()
      if (DEPENDENCY_TYPES.any { trimmedLine.startsWith(it) }) {
        if (seenDependencies.contains(trimmedLine)) {
          println("Removing duplicated dependency: $trimmedLine")
          continue
        } else {
          seenDependencies.add(trimmedLine)
        }
      }
      cleanedLines.add(line)
    }

    try {
      buildFile.writeText(cleanedLines.joinToString("\n"))
    } catch (e: IOException) {
      println("Failed to write updated build file: ${e.message}")
    }
  }

  fun createBackup(): Boolean {
    return try {
      buildFile.copyTo(backupFile, overwrite = true)
      true
    } catch (e: IOException) {
      println("Failed to create backup: ${e.message}")
      false
    }
  }

  fun restoreFromBackup(): Boolean {
    return try {
      if (backupFile.exists()) {
        backupFile.copyTo(buildFile, overwrite = true)
        backupFile.delete()
        true
      } else {
        println("No backup file found: ${backupFile.absolutePath}")
        false
      }
    } catch (e: IOException) {
      println("Failed to restore from backup: ${e.message}")
      false
    }
  }

  fun removeDependency(dependency: String): Boolean {
    if (!buildFile.exists()) {
      println("Build file does not exist: ${buildFile.absolutePath}")
      return false
    }

    val content = buildFile.readText()
    val lines = content.lines().toMutableList()

    // Find and remove the dependency line
    var removed = false
    val iterator = lines.iterator()
    var index = 0

    while (iterator.hasNext()) {
      val line = iterator.next()
      val trimmedLine = line.trim()

      // Check if this line contains our dependency
      if (isMatchingDependencyLine(trimmedLine, dependency)) {
        lines.removeAt(index)
        removed = true
        println("Removed dependency line: ${line.trim()}")
        break
      }
      index++
    }

    if (removed) {
      try {
        buildFile.writeText(lines.joinToString("\n"))
        return true
      } catch (e: IOException) {
        println("Failed to write updated build file: ${e.message}")
        return false
      }
    } else {
      println("Dependency not found in build file: $dependency")
      return false
    }
  }

  private fun isMatchingDependencyLine(line: String, dependency: String): Boolean {
    // More precise matching to avoid partial matches
    val cleanDependency = dependency.substringAfter(" ").trim()
    return line.contains(cleanDependency) && (
        line.startsWith("implementation") ||
            line.startsWith("api") ||
            line.startsWith("testImplementation")
        )
  }

  fun cleanupBackup() {
    if (backupFile.exists()) {
      backupFile.delete()
    }
  }
}