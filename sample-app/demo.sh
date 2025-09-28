#!/bin/bash

# Check if we're in the sample-app directory
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ Error: This script must be run from the sample-app directory"
    echo "Please run: cd sample-app && ./demo.sh"
    exit 1
fi

echo "🚀 FastDependencyAnalyser Plugin Demo"
echo "===================================="
echo ""

echo "📦 Sample project structure:"
echo "├── app (uses: feature-home, library-core; UNUSED: library-data, library-tracker)"
echo "├── feature-home (uses: library-core, library-tracker; UNUSED: library-data)"
echo "├── library-core (standalone)"
echo "├── library-data (UNUSED everywhere - should be detected)"
echo "└── library-tracker (used by feature-home only)"
echo ""

echo "⚠️  NOTE: The plugin must be published to mavenLocal first!"
echo "   Run this from the main project: ./gradlew publishToMavenLocal"
echo ""

echo "🔧 First, compile the project to generate class files..."
./gradlew compileKotlin compileTestKotlin

echo ""
echo "🔍 Running dependency analysis..."
./gradlew checkDependencies

echo ""
echo "📊 Checking generated reports:"
if [ -f "dependency-report.txt" ]; then
    echo "✅ dependency-report.txt created:"
    cat dependency-report.txt
else
    echo "❌ dependency-report.txt not found"
fi

echo ""
echo "🔍 Running analysis with full report..."
./gradlew checkDependencies -PfullReport=true

echo ""
if [ -f "full-dependency-report.txt" ]; then
    echo "✅ full-dependency-report.txt created (showing first 50 lines):"
    head -50 full-dependency-report.txt
else
    echo "❌ full-dependency-report.txt not found"
fi

echo ""
echo "🧹 To automatically cleanup unused dependencies, run:"
echo "  ./gradlew cleanupDependencies"
echo ""

echo "📋 Expected results:"
echo "- app module: library-data and library-tracker should be detected as unused"
echo "- feature-home module: library-data should be detected as unused"
echo "- library-core and library-tracker should be marked as used where referenced"