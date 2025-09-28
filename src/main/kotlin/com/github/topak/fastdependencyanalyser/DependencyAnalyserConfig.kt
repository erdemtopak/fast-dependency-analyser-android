package com.github.topak.fastdependencyanalyser

import org.yaml.snakeyaml.Yaml
import java.io.File

data class DependencyAnalyserConfig(
  val excludedModules: List<String> = emptyList(),
  val excludedDependencies: Map<String, List<String>> = emptyMap(),
) {

  companion object {
    @Suppress("UNCHECKED_CAST")
    fun load(rootDir: File): DependencyAnalyserConfig {
      val configFile = rootDir.resolve("dependency-analyser-config.yml")

      if (!configFile.exists()) {
        println("Config file not found: ${configFile.absolutePath}")
        println("Using default configuration (no exclusions)")
        return DependencyAnalyserConfig()
      }

      return try {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(configFile.readText())

        val excludedModules = (data["excluded-modules"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        val excludedDependenciesRaw = data["excluded-dependencies"] as? Map<String, *>
        val excludedDependencies: Map<String, List<String>> = excludedDependenciesRaw?.mapNotNull { (key, value) ->
          val dependencyList = (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
          if (dependencyList.isNotEmpty()) key to dependencyList else null
        }?.toMap() ?: emptyMap()

        DependencyAnalyserConfig(excludedModules, excludedDependencies)
      } catch (e: Exception) {
        println("Failed to parse config file: ${e.message}")
        println("Using default configuration (no exclusions)")
        DependencyAnalyserConfig()
      }
    }
  }

  fun isModuleExcluded(moduleName: String): Boolean {
    return excludedModules.contains(moduleName)
  }

  fun isDependencyExcluded(moduleName: String, dependency: Dependency): Boolean {
    return excludedDependencies[moduleName]?.contains(dependency.toString()) == true
  }
}