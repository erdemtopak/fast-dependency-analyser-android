# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-09-29

### Added
- Initial release of FastDependencyAnalyser Gradle plugin
- Bytecode analysis for dependency usage detection using ASM library
- Automatic dependency cleanup with build validation and rollback
- Support for multi-module Android/Kotlin projects
- Configurable exclusions via `dependency-analyser-config.yml`
- Comprehensive reporting with detailed and summary modes
- Two main tasks:
  - `checkDependencies`: Analyzes and reports unused dependencies
  - `cleanupDependencies`: Automatically removes unused dependencies with validation
- Support for various dependency formats:
  - Gradle 7+ project dependencies (`projects.library.api`)
  - Legacy project dependencies (`project(":library:api")`)
  - Version catalogs (`libs.retrofit`)
  - Direct dependencies (`"com.example:library:1.0.0"`)
- Smart cleanup algorithms:
  - Binary search for efficient removal of large dependency sets
  - Sequential removal for small sets (≤3 dependencies)
  - Build validation with automatic rollback on failure
- Flexible configuration options:
  - Module-specific analysis (`-Pmodule=feature:home`)
  - Fast mode for skipping compilation (`-PfastMode=true`)
  - Detailed reporting (`-PfullReport=true`)
  - CI/CD integration (`-PfailOnUnused=true`)
- Cross-module dependency analysis
- JitPack publishing support
- Java 17 compatibility
- MIT License

### Technical Details
- Built with Kotlin and Gradle Plugin Development Plugin
- Uses ASM 9.5 for bytecode analysis
- SnakeYAML for configuration parsing
- Kotlin Coroutines for concurrent processing
- Comprehensive error handling and validation

### Documentation
- Complete README with usage examples
- Configuration guide with YAML examples
- Troubleshooting section
- Best practices and CI/CD integration examples