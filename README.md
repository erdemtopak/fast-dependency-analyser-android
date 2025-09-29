# FastDependencyAnalyser

[![](https://jitpack.io/v/erdemtopak/fast-dependency-analyser-android.svg)](https://jitpack.io/#erdemtopak/fast-dependency-analyser-android)

A fast and efficient Gradle plugin for analyzing and automatically removing unused dependencies in Android/Kotlin projects using bytecode analysis.

## Features

🔍 **Fast Bytecode Analysis** - Uses ASM library to analyze compiled classes and detect actual dependency usage

⚡ **Smart Cleanup** - Automatically removes unused dependencies with build validation and rollback

🎯 **Precise Detection** - Analyzes real class references, not just import statements

🔧 **Flexible Configuration** - Exclude specific modules or dependencies from analysis

📊 **Detailed Reporting** - Generate comprehensive reports of dependency usage

## Quick Start

### 1. Add the Plugin

#### Using the plugins DSL (Gradle 2.1+):

```kotlin
plugins {
    id("com.github.topak.fast-dependency-analyser") version "0.0.1"
}
```

#### Using legacy plugin application:

```kotlin
buildscript {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.github.topak:fast-dependency-analyser:0.0.1")
    }
}

apply(plugin = "com.github.topak.fast-dependency-analyser")
```

### 2. Basic Usage

```bash
# First, compile your project to ensure class files are generated
./gradlew compileDebugSources compileDebugUnitTestKotlin

# Find unused dependencies
./gradlew checkDependencies

# Automatically remove unused dependencies
./gradlew cleanupDependencies
```

## Tasks

### `checkDependencies`

Analyzes bytecode to find unused dependencies.

#### Parameters:
- `-PfastMode=true` - Skip compilation step (use with caution)
- `-Pmodule=feature:home` - Analyze specific module only
- `-PfullReport=true` - Generate detailed analysis report
- `-PfailOnUnused=true` - Fail build if unused dependencies are found

#### Examples:

```bash
# Basic analysis
./gradlew checkDependencies

# Analyze specific module with detailed report
./gradlew checkDependencies -Pmodule=feature:home -PfullReport=true

# Use in CI to fail on unused dependencies
./gradlew checkDependencies -PfailOnUnused=true

# Fast mode (skip compilation - use only if classes are up to date)
./gradlew checkDependencies -PfastMode=true
```

#### Output:
- `dependency-report.txt` - Summary of unused dependencies
- `full-dependency-report.txt` - Detailed report (with `-PfullReport=true`)

### `cleanupDependencies`

Automatically removes unused dependencies with build validation.

#### Parameters:
- `-Pmodule=feature:home` - Clean specific module only

#### How it works:
1. Runs `checkDependencies` task automatically
2. Parses unused dependencies from the report
3. Uses binary search algorithm for efficient removal (≤3 deps = sequential)
4. Validates build after each removal
5. Automatically rolls back if compilation fails

#### Examples:

```bash
# Clean all modules
./gradlew cleanupDependencies

# Clean specific module
./gradlew cleanupDependencies -Pmodule=feature:home
```

#### Output:
- `dependency-cleanup-report.txt` - Cleanup results and summary

## Configuration

Create `dependency-analyser-config.yml` in your project root:

```yaml
excluded-modules:
  - "buildSrc"
  - "app"  # Often needs to keep dependencies for runtime

excluded-dependencies:
  "feature:home":
    - "implementation projects.library.analytics"  # Keep for runtime usage
    - "api projects.common.models"  # Keep for API exposure
  "library:api":
    - "implementation libs.retrofit"  # Keep essential dependencies
```

### Configuration Options:

- **excluded-modules**: List of module names to skip during analysis
- **excluded-dependencies**: Map of module names to lists of dependencies to keep

## Supported Dependency Formats

The plugin supports various Gradle dependency declaration formats:

```kotlin
// ✅ Supported formats
implementation(projects.library.api)           // Gradle 7+ project dependencies
implementation(project(":library:api"))        // Legacy project dependencies
implementation(libs.retrofit)                  // Version catalogs
implementation("com.squareup.retrofit2:retrofit:2.9.0")  // Direct dependencies
api(projects.common.models)                    // API dependencies
testImplementation(libs.junit)                 // Test dependencies
```

## How It Works

1. **Compilation Check**: Ensures all modules are compiled and class files are available
2. **Dependency Parsing**: Extracts dependency declarations from both `build.gradle` and `build.gradle.kts` files
3. **Settings File Support**: Reads module configurations from `settings.gradle` or `settings.gradle.kts`
4. **Bytecode Analysis**: Uses ASM to analyze compiled classes and extract:
   - Referenced classes in method calls, field accesses, type usage
   - Generic type parameters and signatures
   - Inheritance relationships
5. **Cross-Module Analysis**: Maps project dependencies to their exposed classes
6. **Usage Detection**: Matches referenced classes against dependency-provided classes
7. **Smart Cleanup**: Uses binary search for efficient removal with build validation

## Best Practices

### Before Running
- ✅ Commit your changes before running cleanup
- ✅ Ensure project compiles: `./gradlew compileDebugSources compileDebugUnitTestKotlin`
- ✅ Review configuration to exclude critical dependencies

### For CI/CD
```bash
# Add to your CI pipeline to prevent unused dependencies
./gradlew checkDependencies -PfailOnUnused=true
```

### Module-Specific Analysis
```bash
# Focus on specific modules during development
./gradlew checkDependencies -Pmodule=feature:payment -PfullReport=true
```

### Configuration Management
- Keep a `dependency-analyser-config.yml` file in version control
- Document why certain dependencies are excluded
- Review exclusions periodically

## Advanced Usage

### Custom Gradle Configuration

```kotlin
// In your build.gradle.kts
tasks.named("checkDependencies") {
    doFirst {
        println("Starting dependency analysis...")
    }
}

tasks.named("cleanupDependencies") {
    dependsOn("test") // Run tests before cleanup
}
```

### Integration with Other Tools

```kotlin
// Run dependency analysis before publishing
tasks.named("publish") {
    dependsOn("checkDependencies")
}
```

## Troubleshooting

### Common Issues

**"Module not found in settings.gradle"**
- Ensure the module name matches exactly what's in `settings.gradle`
- Use `:` separators (e.g., `feature:home`, not `feature.home`)

**"No class files found"**
- Run compilation first: `./gradlew compileDebugSources`
- Check that the target module compiles successfully

**"Build validation failed"**
- Some dependencies might be needed at runtime but not compile-time
- Add them to `excluded-dependencies` in configuration
- Review the error output in the cleanup report

**"External dependencies marked as unused"**
- External dependencies (from Maven/JCenter) are often reflection-based
- Consider excluding them in configuration if they're runtime-required

### Debugging

Enable detailed logging:
```bash
./gradlew checkDependencies -PfullReport=true --info
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a detailed history of changes and releases.