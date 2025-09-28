# Dependency Analysis Tasks

Gradle tasks to find and remove unused dependencies in your Android project.

## Quick Start

```bash
# First compile your project to ensure class files are generated and up to date
./gradlew compileDebugSources compileDebugUnitTestKotlin

# Run following gradle task to find unused dependencies
./gradlew checkDependencies

# Run following gradle task remove unused dependencies (with validation), by using the report generated in previous step
./gradlew cleanupDependencies
```

## checkDependencies

Analyzes bytecode to find unused dependencies.

### Parameters
- `-PfastMode=true` - Skip compilation
- `-Pmodule=feature:home` - Analyze specific module
- `-PfullReport=true` - Detailed report
- `-PfailOnUnused=true` - Fail build if unused deps found

### Output
- `dependency-report.txt` - Summary of unused dependencies
- `full-dependency-report.txt` - Detailed report (with -PfullReport)

## cleanupDependencies  

Automatically removes unused dependencies with build validation.

### Parameters
- `-Pmodule=feature:home` - Clean specific module only

### How it works
1. Runs `checkDependencies` first (automatic dependency)
2. Parses unused dependencies from report
3. Uses binary search for fast removal (≤3 deps = sequential)
4. Validates build after each removal
5. Rolls back if compilation fails

### Output
- `dependency-cleanup-report.txt` - Cleanup results

## Configuration

Create `dependency-analyser-config.yml`:

```yaml
excluded-modules:
  - "buildSrc"

excluded-dependencies:
  "feature:home":
    - "implementation projects.library.required"
```

## Supported Formats

```gradle
implementation(projects.library.api)           # ✓
implementation(project(":library:api"))        # ✓  
implementation(libs.retrofit)                  # ✓
```

## Best Practices

- Commit changes before cleanup
- Compile first: `./gradlew compileDebugSources compileDebugUnitTestKotlin`
- Use in CI: `./gradlew checkDependencies -PfailOnUnused=true`