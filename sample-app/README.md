# Sample App - FastDependencyAnalyser Demo

This sample application demonstrates the FastDependencyAnalyser Gradle plugin behavior with internal project dependencies.

## Project Structure

```
sample-app/
├── app/                 # Main application module
├── feature-home/        # Feature module
├── library-core/        # Core library (used by app & feature-home)
├── library-data/        # Data library (UNUSED - should be detected)
└── library-tracker/     # Analytics library (used by feature-home only)
```

## Dependency Graph

```
app
├── feature-home ✅ (USED - HomeController is imported)
├── library-core ✅ (USED - Logger and User classes are imported)
├── library-data ❌ (UNUSED - no classes imported)
└── library-tracker ❌ (UNUSED - no classes imported)

feature-home
├── library-core ✅ (USED - DataService, Logger, User are imported)
├── library-data ❌ (UNUSED - no classes imported)
└── library-tracker ✅ (USED - Analytics class is imported)

library-core
└── (no dependencies)

library-data
└── (no dependencies)

library-tracker
└── (no dependencies)
```

## How to Run the Demo

### Prerequisites
First, the plugin must be published to Maven Local. From the main project directory:
```bash
./gradlew publishToMavenLocal
```

### Option 1: Quick Demo Script (from sample-app directory)
```bash
cd sample-app
./demo.sh
```

### Option 2: Manual Steps

1. **Compile the project** (required for bytecode analysis):
   ```bash
   ./gradlew compileKotlin compileTestKotlin
   ```

2. **Run dependency analysis**:
   ```bash
   ./gradlew checkDependencies
   ```

3. **View the report**:
   ```bash
   cat dependency-report.txt
   ```

4. **Get detailed report**:
   ```bash
   ./gradlew checkDependencies -PfullReport=true
   cat full-dependency-report.txt
   ```

5. **Analyze specific module**:
   ```bash
   ./gradlew checkDependencies -Pmodule=app -PfullReport=true
   ```

6. **Automatically remove unused dependencies**:
   ```bash
   ./gradlew cleanupDependencies
   ```

## Expected Results

The plugin should detect these unused internal dependencies:

- **app module**: `library-data` and `library-tracker` (not referenced in code)
- **feature-home module**: `library-data` (not referenced in code)

Used dependencies should be correctly identified:
- **app** → **feature-home**: ✅ (HomeController class used)
- **app** → **library-core**: ✅ (Logger, User classes used)
- **feature-home** → **library-core**: ✅ (DataService, Logger, User classes used)
- **feature-home** → **library-tracker**: ✅ (Analytics class used)

## Key Points

- The plugin only analyzes **internal project dependencies** (`project(":module-name")`)
- External dependencies (from Maven/JCenter) are ignored
- Analysis is based on **bytecode inspection**, not import statements
- The plugin requires compiled class files to work properly