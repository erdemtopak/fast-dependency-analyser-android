package com.github.topak.fastdependencyanalyser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule.module
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

val DEPENDENCY_TYPES = setOf("implementation", "api", "testImplementation")

private const val MAX_DISPLAYED_CLASSES = 10
private const val SUMMARY_SEPARATOR_LENGTH = 80
private const val PERCENTAGE_FORMAT = "%.1f"

abstract class DependencyCheckerTask : DefaultTask() {

  @InputDirectory
  val rootDir: File = project.rootDir

  @Input
  val fastMode: Boolean = project.hasProperty("fastMode") && project.property("fastMode").toString().toBoolean()

  @Input
  val fullReportEnabled: Boolean = project.hasProperty("fullReport") && project.property("fullReport").toString().toBoolean()

  @Input
  @Optional
  val targetModule: String? = if (project.hasProperty("module")) project.property("module").toString() else null

  @Input
  val failOnUnused: Boolean = project.hasProperty("failOnUnused") && project.property("failOnUnused").toString().toBoolean()

  private val depRegex = Regex("^(implementation|api|testImplementation)\\s*\\(?\\s*")

  init {
    if (!fastMode) {
      project.subprojects.forEach { subproject ->
        listOf("compileDebugSources", "compileDebugUnitTestSources").forEach { taskName ->
          if (subproject.tasks.findByName(taskName) != null) {
            dependsOn("${subproject.path}:$taskName")
          }
        }
      }
    }
  }

  @TaskAction
  fun checkDependencies() {
    val config = DependencyAnalyserConfig.load(rootDir)

    val modules = parseModules(config)

    if (fullReportEnabled) {
      printDetailedReport(modules)
    } else {
      printCleanReport(modules)
    }
  }

  private fun parseModules(config: DependencyAnalyserConfig): List<Module> {
    val allModuleNames = try {
      val settingsFile = rootDir.resolve("settings.gradle")
      val settingsFileKts = rootDir.resolve("settings.gradle.kts")

      val actualSettingsFile = when {
        settingsFile.exists() -> settingsFile
        settingsFileKts.exists() -> settingsFileKts
        else -> {
          println("No settings.gradle or settings.gradle.kts file found in ${rootDir.absolutePath}")
          return emptyList()
        }
      }

      actualSettingsFile.readLines()
    } catch (e: Exception) {
      println("Failed to read settings file: ${e.message}")
      return emptyList()
    }
      .filter { it.startsWith("include") }
      .map { line ->
        line
          .replace("include", "")
          .replace("'", "")
          .replace("\"", "")
          .replace("(", "")
          .replace(")", "")
          .trim()
          .substring(1)
      }
      .filter { it != "app" }
      .filterNot { config.isModuleExcluded(it) }

    return runBlocking {
      // Parse all modules for dependency analysis, but we need all modules for cross-references
      val allModules = allModuleNames.map { moduleName ->
        async(Dispatchers.IO) {
          Module(
            name = moduleName,
            dependencies = parseDependencies(moduleName),
            referencedClasses = analyzeClassFiles(moduleName),
            exposedClasses = getModuleExposedClasses(moduleName),
          )
        }
      }.awaitAll()

      // Calculate unused dependencies for all modules (needed for cross-references)
      val allModulesWithUnused = allModules.map {
        it.copy(unusedDependencies = findUnusedDependencies(it, allModules, config))
      }

      // Return only the requested modules, but with analysis based on all modules
      return@runBlocking if (targetModule != null) {
        val filteredModules = allModulesWithUnused.filter { it.name == targetModule }
        if (filteredModules.isEmpty()) {
          println("Module '$targetModule' not found in settings.gradle")
          return@runBlocking emptyList()
        }
        filteredModules
      } else {
        allModulesWithUnused
      }
    }
  }

  private fun parseDependencies(moduleName: String): List<Dependency> {
    val modulePath = moduleName.replace(':', '/')
    val buildFile = rootDir.resolve("$modulePath/build.gradle")
    val buildFileKts = rootDir.resolve("$modulePath/build.gradle.kts")

    return when {
      buildFile.exists() -> parseDependenciesFromFile(buildFile)
      buildFileKts.exists() -> parseDependenciesFromFile(buildFileKts)
      else -> emptyList()
    }
  }

  private fun parseDependenciesFromFile(file: File): List<Dependency> {
    return file.readLines()
      .map { it.trim() }
      .filter { trimmedLine -> DEPENDENCY_TYPES.any { trimmedLine.startsWith(it) } }
      .mapNotNull { line -> extractDependency(line) }
  }

  private fun extractDependency(line: String): Dependency? {
    // Extract dependency type from the line
    val depType = when {
      line.startsWith("api") -> Dependency.Type.API
      line.startsWith("testImplementation") -> Dependency.Type.TEST_IMPLEMENTATION
      line.startsWith("implementation") -> Dependency.Type.IMPLEMENTATION
      else -> return null
    }

    // Extract the full dependency name including prefixes
    val fullDependencyName = line
      .replaceFirst(depRegex, "")
      .trim()

    // Handle project(":module:name") syntax
    val sanitizedDependency = if (fullDependencyName.startsWith("project(")) {
      // Extract project path from project(":library-core") -> :library-core
      val projectPath = fullDependencyName
        .substringAfter("project(")
        .substringBefore(")")
        .replace("\"", "")
        .replace("'", "")
        .replace(":", "")
        .trim()

      projectPath
    } else { // Handle projects.library.name syntax
      fullDependencyName
        .substringAfter("(")
        .substringBefore(")")
        .substringBefore("{")
        .substringBefore("//")
        .substringBefore("because")
        .trim()
        .removePrefix("\"")
        .removePrefix("'")
        .removeSuffix("\"")
        .removeSuffix("'")
    }

    if (sanitizedDependency.isNotEmpty() && sanitizedDependency != line) {
      return Dependency(name = sanitizedDependency, type = depType)
    }
    return null
  }

  private fun analyzeClassFiles(moduleName: String): Set<String> {
    val modulePath = moduleName.replace(':', '/')
    val referencedClasses = mutableSetOf<String>()

    // Check both Java and Kotlin compiled classes
    val buildDirs = listOf(
      rootDir.resolve("$modulePath/build/classes/java/main"),
      rootDir.resolve("$modulePath/build/classes/kotlin/main"),
      rootDir.resolve("$modulePath/build/tmp/kotlin-classes/debug"),
      rootDir.resolve("$modulePath/build/tmp/kotlin-classes/debugUnitTest"),
    )

    buildDirs.forEach { buildDir ->
      if (buildDir.exists()) {
        buildDir.walkTopDown()
          .filter { it.isFile && it.extension == "class" }
          .forEach { classFile ->
            try {
              referencedClasses.addAll(extractReferencesFromClassFile(classFile))
            } catch (e: Exception) {
              // Skip problematic class files
              println("Warning: Could not analyze ${classFile.path}: ${e.message}")
            }
          }
      }
    }

    return referencedClasses
  }

  private fun extractReferencesFromClassFile(classFile: File): Set<String> {
    val referencedClasses = mutableSetOf<String>()

    try {
      val classReader = ClassReader(classFile.readBytes())

      classReader.accept(
        object : ClassVisitor(Opcodes.ASM9) {

          override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
          ) {
            // Add superclass reference
            superName?.let { referencedClasses.add(it.replace('/', '.')) }

            // Add interface references
            interfaces?.forEach { interfaceName ->
              referencedClasses.add(interfaceName.replace('/', '.'))
            }
          }

          override fun visitField(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            value: Any?,
          ): FieldVisitor? {
            // Extract field type references
            descriptor?.let { desc ->
              extractTypesFromDescriptor(desc).forEach { type ->
                referencedClasses.add(type)
              }
            }
            return null
          }

          override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?,
          ): MethodVisitor? {
            // Extract method parameter and return type references
            descriptor?.let { desc ->
              extractTypesFromDescriptor(desc).forEach { type ->
                referencedClasses.add(type)
              }
            }

            // Also check generic signature for additional type information
            signature?.let { sig ->
              extractTypesFromSignature(sig).forEach { type ->
                referencedClasses.add(type)
              }
            }

            // Extract exception type references
            exceptions?.forEach { exception ->
              referencedClasses.add(exception.replace('/', '.'))
            }

            return object : MethodVisitor(Opcodes.ASM9) {
              override fun visitTypeInsn(opcode: Int, type: String?) {
                // NEW, ANEWARRAY, CHECKCAST, INSTANCEOF instructions
                type?.let { referencedClasses.add(it.replace('/', '.')) }
              }

              override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
                // GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC instructions
                owner?.let { referencedClasses.add(it.replace('/', '.')) }
                descriptor?.let { desc ->
                  extractTypesFromDescriptor(desc).forEach { refType ->
                    referencedClasses.add(refType)
                  }
                }
              }

              override fun visitMethodInsn(
                opcode: Int,
                owner: String?,
                name: String?,
                descriptor: String?,
                isInterface: Boolean,
              ) {
                // Method invocation references
                owner?.let { referencedClasses.add(it.replace('/', '.')) }
                descriptor?.let { desc ->
                  extractTypesFromDescriptor(desc).forEach { refType ->
                    referencedClasses.add(refType)
                  }
                }
              }
            }
          }
        },
        0,
      )
    } catch (e: Exception) {
      // Return empty set if class file cannot be parsed
      return emptySet()
    }

    return referencedClasses.filter { it.isNotEmpty() && !it.startsWith("[") }.toSet()
  }

  private fun extractTypesFromDescriptor(descriptor: String): List<String> {
    val types = mutableListOf<String>()

    try {
      // Parse method descriptor for parameter and return types
      if (descriptor.contains("(")) {
        val methodType = Type.getMethodType(descriptor)

        // Add return type
        if (methodType.returnType.sort == Type.OBJECT) {
          types.add(methodType.returnType.className)
        }

        // Add parameter types
        methodType.argumentTypes.forEach { argType ->
          if (argType.sort == Type.OBJECT) {
            types.add(argType.className)
          }
        }
      } else {
        // Parse field descriptor
        val fieldType = Type.getType(descriptor)
        if (fieldType.sort == Type.OBJECT) {
          types.add(fieldType.className)
        }
      }
    } catch (e: Exception) {
      // Skip malformed descriptors
    }

    return types
  }

  private fun extractTypesFromSignature(signature: String): List<String> {
    val types = mutableListOf<String>()

    // Parse generic signatures to extract type information
    // Example: (Ljava/lang/String;)Lkotlin/Result<Lcom/flink/consumer/commons/models/Product;>;
    val typePattern = Regex("L([^;<]+);")

    typePattern.findAll(signature).forEach { match ->
      val className = match.groupValues[1].replace('/', '.')
      types.add(className)
    }

    return types
  }

  private fun getModuleExposedClasses(moduleName: String): Set<String> {
    val modulePath = moduleName.replace(':', '/')
    val exposedClasses = mutableSetOf<String>()

    // Check both Java and Kotlin compiled classes
    val buildDirs = listOf(
      rootDir.resolve("$modulePath/build/classes/java/main"),
      rootDir.resolve("$modulePath/build/classes/kotlin/main"),
      rootDir.resolve("$modulePath/build/tmp/kotlin-classes/debug"), // Android
      rootDir.resolve("$modulePath/build/tmp/kotlin-classes/release"), // Android
    )

    buildDirs.forEach { buildDir ->
      if (buildDir.exists()) {
        buildDir.walkTopDown()
          .filter { it.isFile && it.extension == "class" }
          .forEach { classFile ->
            try {
              val className = extractClassNameFromClassFile(classFile)
              if (className != null && isPublicClass(classFile)) {
                exposedClasses.add(className)
              }
            } catch (e: Exception) {
              // Skip problematic class files
            }
          }
      }
    }

    return exposedClasses
  }

  private fun extractClassNameFromClassFile(classFile: File): String? {
    return try {
      val classReader = ClassReader(classFile.readBytes())
      val className = classReader.className?.replace('/', '.')
      className
    } catch (e: Exception) {
      null
    }
  }

  private fun isPublicClass(classFile: File): Boolean {
    return try {
      val classReader = ClassReader(classFile.readBytes())
      var isPublic = false

      classReader.accept(
        object : ClassVisitor(Opcodes.ASM9) {
          override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
          ) {
            // Check if class has public or package-private access (not private)
            isPublic = (access and Opcodes.ACC_PUBLIC) != 0 ||
                (access and Opcodes.ACC_PRIVATE) == 0
          }
        },
        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
      )

      isPublic
    } catch (e: Exception) {
      false
    }
  }

  private fun findUnusedDependencies(
    module: Module,
    allModules: List<Module>,
    config: DependencyAnalyserConfig,
  ): List<Dependency> {
    return runBlocking {
      module.dependencies.map { dependency ->
        async(Dispatchers.Default) {
          if (config.isDependencyExcluded(moduleName = module.name, dependency = dependency)) {
            return@async null
          }

          // Convert dependency format (library.something) to module name (library:something)
          // Handle case conversion: vouchersUtil -> vouchers-util
          val targetModuleNameKebapCase = convertDependencyToModuleName(dependency.name)
          val targetModuleName = convertDependencyToModuleName(dependency.name, convertCamelToKebapCase = false)

          // Find the corresponding module
          val targetModule = allModules.find { it.name == targetModuleNameKebapCase || it.name == targetModuleName }

          if (targetModule == null || dependency.name.startsWith("libs.")) {
            // If target module is not found, consider it as unused (might be external dependency)
            // Always consider libs.* dependencies as used (they are external libraries)
            return@async null
          }

          val isUsed = module.referencedClasses.any { referencedClass ->
            // Check exact class match or if referenced class matches any target declaration
            targetModule.exposedClasses.any { exposedClass ->
              exposedClass.equals(referencedClass, ignoreCase = true)
            }
          }

          if (isUsed) null else dependency
        }
      }.awaitAll().filterNotNull()
    }
  }

  private fun convertDependencyToModuleName(dependency: String, convertCamelToKebapCase: Boolean = true): String {
    // Remove projects. prefix if present
    val cleanDependency = dependency.removePrefix("projects.")

    // Convert dependency format (library.api.cart.pub) to module name (library:api:cart:pub)
    // Need to handle camelCase to kebab-case conversion
    return cleanDependency.split('.').joinToString(":") { part ->
      // Convert camelCase to kebab-case
      return@joinToString if (convertCamelToKebapCase) {
        part.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
      } else
        part
    }
  }

  private fun printCleanReport(modules: List<Module>) {
    val reportFile = rootDir.resolve("dependency-report.txt")
    val reportContent = StringBuilder()

    val header = if (targetModule != null) {
      "Unused Dependencies Analysis for module: $targetModule"
    } else {
      "Unused Dependencies Analysis:"
    }
    val separator = "=".repeat(header.length)

    // Print to console
    println(header)
    println(separator)

    // Add to report file
    reportContent.appendLine(header)
    reportContent.appendLine(separator)

    val modulesWithUnusedDeps = modules.filter { it.unusedDependencies.isNotEmpty() }

    if (modulesWithUnusedDeps.isEmpty()) {
      val message = "No unused dependencies found! 🎉🎉🎉"
      println(message)
      reportContent.appendLine(message)
      reportFile.writeText(reportContent.toString())
      return
    }

    modulesWithUnusedDeps.forEach { module ->
      if (module.unusedDependencies.isNotEmpty()) {
        val moduleHeader = "\nModule: ${module.name}"
        println(moduleHeader)
        reportContent.appendLine(moduleHeader)

        module.unusedDependencies.forEach { unused ->
          val dependencyLine = "    - ${unused.type.value} ${unused.name}"
          println(dependencyLine)
          reportContent.appendLine(dependencyLine)
        }
      }
    }

    // Write report to file
    reportFile.writeText(reportContent.toString())
    println("\nReport saved to: ${reportFile.absolutePath}")

    // Fail task if requested and unused dependencies found
    val totalUnused = modulesWithUnusedDeps.sumOf { it.unusedDependencies.size }
    if (failOnUnused && totalUnused > 0) {
      throw org.gradle.api.GradleException("Task failed: Found $totalUnused unused dependencies. Remove them or run without -PfailOnUnused=true")
    }
  }

  private fun printDetailedReport(modules: List<Module>) {
    val reportFile = rootDir.resolve("full-dependency-report.txt")
    val reportContent = StringBuilder()

    val reportTitle = "DETAILED MODULE ANALYSIS REPORT"
    val titleSeparator = "═".repeat(reportTitle.length)

    // Print to console
    println()
    println(reportTitle)
    println(titleSeparator)
    println("Total modules analyzed: ${modules.size}")
    println()

    // Add to report file
    reportContent.appendLine()
    reportContent.appendLine(reportTitle)
    reportContent.appendLine(titleSeparator)
    reportContent.appendLine("Total modules analyzed: ${modules.size}")
    reportContent.appendLine()

    modules.forEachIndexed { index, module ->
      val moduleHeader = "MODULE: ${module.name}"
      val moduleSeparator = "─".repeat(moduleHeader.length)

      // Print to console and add to report
      println(moduleHeader)
      println(moduleSeparator)
      reportContent.appendLine(moduleHeader)
      reportContent.appendLine(moduleSeparator)

      // Dependencies Section
      val depsHeader = "DEPENDENCIES (${module.dependencies.size} total)"
      println(depsHeader)
      reportContent.appendLine(depsHeader)
      if (module.dependencies.isEmpty()) {
        val noneLine = "   └─ (none)"
        println(noneLine)
        reportContent.appendLine(noneLine)
      } else {
        module.dependencies.forEachIndexed { depIndex, dep ->
          val isLast = depIndex == module.dependencies.size - 1
          val prefix = if (isLast) "   └─" else "   ├─"
          val depLine = "$prefix ${dep.type.value} ${dep.name}"
          println(depLine)
          reportContent.appendLine(depLine)
        }
      }

      println()
      reportContent.appendLine()

      // Referenced Classes Section
      val referencedHeader = "REFERENCED CLASSES (${module.referencedClasses.size} total)"
      println(referencedHeader)
      reportContent.appendLine(referencedHeader)
      if (module.referencedClasses.isEmpty()) {
        val noneLine = "   └─ (none)"
        println(noneLine)
        reportContent.appendLine(noneLine)
      } else {
        val displayClasses = module.referencedClasses.take(MAX_DISPLAYED_CLASSES) // Show more for detailed report
        displayClasses.forEachIndexed { classIndex, ref ->
          val isLast = classIndex == displayClasses.size - 1 && module.referencedClasses.size <= MAX_DISPLAYED_CLASSES
          val prefix = if (isLast) "   └─" else "   ├─"
          val refLine = "$prefix $ref"
          println(refLine)
          reportContent.appendLine(refLine)
        }
        if (module.referencedClasses.size > MAX_DISPLAYED_CLASSES) {
          val moreLine = "   └─ ... and ${module.referencedClasses.size - MAX_DISPLAYED_CLASSES} more classes"
          println(moreLine)
          reportContent.appendLine(moreLine)
        }
      }

      println()
      reportContent.appendLine()

      // Exposed Classes Section
      val exposedHeader = "EXPOSED CLASSES (${module.exposedClasses.size} total)"
      println(exposedHeader)
      reportContent.appendLine(exposedHeader)
      if (module.exposedClasses.isEmpty()) {
        val noneLine = "   └─ (none)"
        println(noneLine)
        reportContent.appendLine(noneLine)
      } else {
        val displayExposed = module.exposedClasses.take(MAX_DISPLAYED_CLASSES)
        displayExposed.forEachIndexed { expIndex, exposed ->
          val isLast = expIndex == displayExposed.size - 1 && module.exposedClasses.size <= MAX_DISPLAYED_CLASSES
          val prefix = if (isLast) "   └─" else "   ├─"
          val expLine = "$prefix $exposed"
          println(expLine)
          reportContent.appendLine(expLine)
        }
        if (module.exposedClasses.size > MAX_DISPLAYED_CLASSES) {
          val moreLine = "   └─ ... and ${module.exposedClasses.size - MAX_DISPLAYED_CLASSES} more classes"
          println(moreLine)
          reportContent.appendLine(moreLine)
        }
      }

      println()
      reportContent.appendLine()

      val unusedHeader = "UNUSED DEPENDENCIES (${module.unusedDependencies.size} total)"
      println(unusedHeader)
      reportContent.appendLine(unusedHeader)
      if (module.unusedDependencies.isEmpty()) {
        val successLine = "   └─ All dependencies are used!"
        println(successLine)
        reportContent.appendLine(successLine)
      } else {
        module.unusedDependencies.forEachIndexed { unusedIndex, unused ->
          val isLast = unusedIndex == module.unusedDependencies.size - 1
          val prefix = if (isLast) "   └─" else "   ├─"
          val unusedLine = "$prefix ${unused.type.value} ${unused.name}"
          println(unusedLine)
          reportContent.appendLine(unusedLine)
        }
      }

      // Module separator (don't add after last module)
      if (index < modules.size - 1) {
        println()
        println("═".repeat(SUMMARY_SEPARATOR_LENGTH))
        println()
        reportContent.appendLine()
        reportContent.appendLine("═".repeat(SUMMARY_SEPARATOR_LENGTH))
        reportContent.appendLine()
      }
    }

    // Summary at the end
    val totalDeps = modules.sumOf { it.dependencies.size }
    val totalUnused = modules.sumOf { it.unusedDependencies.size }
    val totalReferenced = modules.sumOf { it.referencedClasses.size }
    val totalExposed = modules.sumOf { it.exposedClasses.size }

    println()
    reportContent.appendLine()

    val summaryHeader = "ANALYSIS SUMMARY"
    val summarySeparator = "═".repeat(20)
    println(summaryHeader)
    println(summarySeparator)
    reportContent.appendLine(summaryHeader)
    reportContent.appendLine(summarySeparator)

    val depsLine = "Total Dependencies:     $totalDeps"
    val unusedLine = "Total Unused:           $totalUnused"
    val referencedLine = "Total Referenced Classes: $totalReferenced"
    val exposedLine = "Total Exposed Classes:   $totalExposed"
    val cleanupLine =
      "Cleanup Potential:      ${if (totalDeps > 0) PERCENTAGE_FORMAT.format((totalUnused.toDouble() / totalDeps) * 100) else "0.0"}%"

    println(depsLine)
    println(unusedLine)
    println(referencedLine)
    println(exposedLine)
    println(cleanupLine)

    reportContent.appendLine(depsLine)
    reportContent.appendLine(unusedLine)
    reportContent.appendLine(referencedLine)
    reportContent.appendLine(exposedLine)
    reportContent.appendLine(cleanupLine)

    println()
    reportContent.appendLine()

    // Write detailed report to file
    reportFile.writeText(reportContent.toString())
    println("Full report saved to: ${reportFile.absolutePath}")

    // Fail task if requested and unused dependencies found
    if (failOnUnused && totalUnused > 0) {
      throw org.gradle.api.GradleException("Task failed: Found $totalUnused unused dependencies. Remove them or run without -PfailOnUnused=true")
    }
  }
}

data class Module(
  val name: String,
  val dependencies: List<Dependency>,
  val referencedClasses: Set<String>,
  val exposedClasses: Set<String> = emptySet(),
  val unusedDependencies: List<Dependency> = emptyList(),
)

data class Dependency(
  val name: String,
  val type: Type,
) {
  enum class Type(val value: String) {
    API("api"),
    IMPLEMENTATION("implementation"),
    TEST_IMPLEMENTATION("testImplementation"),
  }

  override fun toString(): String = "${type.value} $name"
}