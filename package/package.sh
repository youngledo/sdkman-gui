#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "project root: $PROJECT_ROOT"

ARCH=$(uname -m)
case "$ARCH" in
    "arm64"|"aarch64") ARCH_SUFFIX="arm64" ;;
    "x86_64"|"amd64") ARCH_SUFFIX="x86_64" ;;
    *) ARCH_SUFFIX="unknown" ;;
esac

echo "architecture: $ARCH ($ARCH_SUFFIX)"

# Accept version as first argument, or extract from Maven if not provided
if [ -n "$1" ]; then
    VERSION="$1"
    echo "Using provided version: $VERSION"
    MVN_VERSION_ARG="-Drevision=$VERSION"
else
    VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | awk '{print $NF}')
    echo "Using version from pom.xml: $VERSION"
    MVN_VERSION_ARG=""
fi

PROJECT_NAME=$(mvn help:evaluate -Dexpression=project.name -q -DforceStdout | awk '{print $NF}')
APP_NAME="SDKMAN GUI"

echo "project name: $PROJECT_NAME"
echo "version: $VERSION"

mvn clean package jpackage:jpackage -DskipTests "$MVN_VERSION_ARG"

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
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux: Rename DEB and RPM files
    if ls target/distributions/*.deb 1> /dev/null 2>&1; then
        DEB_ORIGINAL=$(ls target/distributions/*.deb | head -n 1)
        DEB_RENAMED="target/distributions/${PROJECT_NAME}_${VERSION}_${ARCH_SUFFIX}.deb"
        echo "Renaming DEB file..."
        mv "$DEB_ORIGINAL" "$DEB_RENAMED"
        echo "✓ Generated: $DEB_RENAMED"
    fi

    if ls target/distributions/*.rpm 1> /dev/null 2>&1; then
        RPM_ORIGINAL=$(ls target/distributions/*.rpm | head -n 1)
        RPM_RENAMED="target/distributions/${PROJECT_NAME}_${VERSION}_${ARCH_SUFFIX}.rpm"
        echo "Renaming RPM file..."
        mv "$RPM_ORIGINAL" "$RPM_RENAMED"
        echo "✓ Generated: $RPM_RENAMED"
    fi
fi

echo "✓ Completed!"
ls -lh target/distributions/
