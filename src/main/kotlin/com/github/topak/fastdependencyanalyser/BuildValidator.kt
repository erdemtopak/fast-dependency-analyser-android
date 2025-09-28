package com.github.topak.fastdependencyanalyser

import java.io.File

private const val THREAD_TIMEOUT_MS = 5000L

class BuildValidator(private val rootDir: File) {

  data class BuildResult(
    val success: Boolean,
    val output: String,
    val errorOutput: String,
    val exitCode: Int,
    val duration: Long,
  )

  fun validateFullBuild(): BuildResult {
    return executeBuild(listOf("assembleDebug", "testDebugUnitTest"))
  }

  fun quickCompileCheck(moduleName: String): BuildResult {
    val moduleAssembleTask = ":$moduleName:assembleDebug"
    val moduleTestCompileTask = ":$moduleName:compileDebugUnitTestKotlin"
    return executeBuild(listOf(moduleAssembleTask, moduleTestCompileTask))
  }

  private fun executeBuild(tasks: List<String>): BuildResult {
    val startTime = System.currentTimeMillis()

    return try {
      // Create ProcessBuilder for gradle execution
      val command = mutableListOf("./gradlew")
      command.addAll(tasks)
      command.add("--quiet")  // Suppress most output
      command.add("--no-configuration-cache")  // Avoid configuration cache warnings

      println("Executing: ${command.joinToString(" ")}")

      val processBuilder = ProcessBuilder(command)
      processBuilder.directory(rootDir)
      processBuilder.redirectErrorStream(false)

      val process = processBuilder.start()

      // Read output streams
      val outputBuilder = StringBuilder()
      val errorBuilder = StringBuilder()

      // Read stdout
      val outputReader = process.inputStream.bufferedReader()
      val errorReader = process.errorStream.bufferedReader()

      // Read output in separate threads to prevent deadlock
      val outputThread = Thread {
        outputReader.useLines { lines ->
          lines.forEach { line ->
            outputBuilder.appendLine(line)
            // Only print critical messages
            if (line.contains("BUILD FAILED") || line.contains("BUILD SUCCESSFUL") ||
              line.contains("FAILURE:") || line.contains("ERROR:") ||
              line.contains("Exception:") || line.contains("Compilation error")
            ) {
              println("    $line")
            }
          }
        }
      }

      val errorThread = Thread {
        errorReader.useLines { lines ->
          lines.forEach { line ->
            errorBuilder.appendLine(line)
            // Only print actual errors, not warnings
            if (line.contains("error:") || line.contains("Error:") ||
              line.contains("Exception:") || line.contains("FAILURE:")
            ) {
              println("    ERROR: $line")
            }
          }
        }
      }

      outputThread.start()
      errorThread.start()

      val exitCode = process.waitFor()

      outputThread.join(THREAD_TIMEOUT_MS)
      errorThread.join(THREAD_TIMEOUT_MS)

      val duration = System.currentTimeMillis() - startTime
      val success = exitCode == 0

      if (success) {
        println("Build succeeded in ${duration}ms")
      } else {
        println("Build failed with exit code $exitCode in ${duration}ms")
      }

      BuildResult(
        success = success,
        output = outputBuilder.toString(),
        errorOutput = errorBuilder.toString(),
        exitCode = exitCode,
        duration = duration,
      )

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      println("Build execution failed: ${e.message}")

      BuildResult(
        success = false,
        output = "",
        errorOutput = "Build execution failed: ${e.message}",
        exitCode = -1,
        duration = duration,
      )
    }
  }
}