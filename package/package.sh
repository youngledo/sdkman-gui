#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "project root: $PROJECT_ROOT"

ARCH=$(uname -m)
case "$ARCH" in
    "arm64"|"aarch64") ARCH_SUFFIX="arm64" ;;
    "x86_64") ARCH_SUFFIX="amd64" ;;
    *) ARCH_SUFFIX="unknown" ;;
esac

echo "architecture: $ARCH ($ARCH_SUFFIX)"

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
PROJECT_NAME=$(mvn help:evaluate -Dexpression=project.name -q -DforceStdout)
APP_NAME="SDKMAN GUI"

echo "project name: $PROJECT_NAME"
echo "version: $VERSION"

mvn clean package jpackage:jpackage -DskipTests

# Rename distribution files based on OS
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS: Rename DMG file
    DMG_ORIGINAL="target/distributions/${APP_NAME}-${VERSION}.dmg"
    DMG_RENAMED="target/distributions/${PROJECT_NAME}_${VERSION}_${ARCH_SUFFIX}.dmg"

    if [ -f "$DMG_ORIGINAL" ]; then
        echo "Renaming DMG file..."
        mv "$DMG_ORIGINAL" "$DMG_RENAMED"
        echo "✓ Generated: $DMG_RENAMED"
    fi
elif [[ "$OSTYPE" == "msys"* || "$OSTYPE" == "cygwin"* || "$OSTYPE" == "win32"* ]]; then
    # Windows: Rename EXE file
    EXE_ORIGINAL="target/distributions/${APP_NAME}-${VERSION}.exe"
    EXE_RENAMED="target/distributions/${PROJECT_NAME}_${VERSION}_${ARCH_SUFFIX}.exe"

    if [ -f "$EXE_ORIGINAL" ]; then
        echo "Renaming EXE file..."
        mv "$EXE_ORIGINAL" "$EXE_RENAMED"
        echo "✓ Generated: $EXE_RENAMED"
    fi
fi

echo "✓ Completed!"
ls -lh target/distributions/
