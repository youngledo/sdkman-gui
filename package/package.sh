#!/bin/bash

# SDKMAN GUI Packaging Script
# Simple script to create native installers using jpackage
# Supports Linux (DEB), macOS (DMG), and Windows (MSI)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project configuration
APP_NAME="SDKMAN GUI"
APP_ID="com.sdkman.sdkman-gui"
APP_VERSION="1.0.0"
MAIN_CLASS="io.sdkman.SdkmanApplication"
VENDOR="SDKMAN GUI Team"
PACKAGE_NAME="sdkman-gui"

# Directories
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${PROJECT_DIR}/target"
PACKAGE_DIR="${PROJECT_DIR}/package/"
BUILD_DIR="${TARGET_DIR}/jpackage-build"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to detect platform and architecture
detect_platform() {
    case "$(uname -s)" in
        Linux*)     PLATFORM="linux" ;;
        Darwin*)    PLATFORM="macos" ;;
        CYGWIN*|MINGW*|MSYS*) PLATFORM="windows" ;;
        *)          PLATFORM="unknown" ;;
    esac

    # Detect architecture (use Ubuntu naming convention)
    case "$(uname -m)" in
        x86_64) ARCH="amd64" ;;
        aarch64|arm64) ARCH="arm64" ;;
        armv7l) ARCH="armhf" ;;
        *) ARCH="unknown" ;;
    esac

    print_status "Detected platform: $PLATFORM ($ARCH)"
}

# Function to check requirements
check_requirements() {
    print_status "Checking requirements..."

    # Check Java version
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        print_error "Java 17 or higher is required. Found: Java $JAVA_VERSION"
        exit 1
    fi
    print_success "Java version: $JAVA_VERSION ✓"

    # Check jpackage availability
    if ! command -v jpackage &> /dev/null; then
        print_error "jpackage is not available. Please install JDK 17 or higher."
        exit 1
    fi
    print_success "jpackage available ✓"

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed or not in PATH"
        exit 1
    fi
    print_success "Maven available ✓"

    # Linux-specific checks
    if [ "$PLATFORM" = "linux" ]; then
        print_status "Performing Linux-specific checks..."

        # Check if running from external drive (common permission issue source)
        PROJECT_DIR_MOUNT=$(df -T "$(pwd)" | tail -1 | awk '{print $2}')
        if [[ "$PROJECT_DIR_MOUNT" =~ ^(ntfs|exfat|vfat)$ ]]; then
            print_warning "Running from $PROJECT_DIR_MOUNT filesystem may cause permission issues"
            print_warning "Consider copying the project to an ext4/xfs filesystem"
        fi

        # Check if target directory is writable
        if [ ! -w "$PROJECT_DIR" ]; then
            print_error "Project directory is not writable: $PROJECT_DIR"
            print_error "Try: chmod -R 755 $PROJECT_DIR"
            exit 1
        fi

        # Check for fakeroot (required for DEB package creation)
        if ! command -v fakeroot &> /dev/null; then
            print_warning "fakeroot not found. fakeroot is required for DEB package creation."
            echo "To install fakeroot, run:"
            echo "    sudo apt-get install fakeroot"

            # Ask user if they want to install fakeroot
            echo
            read -p "Do you want to install fakeroot now? (y/N): " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                print_status "Installing fakeroot (sudo required)..."
                if sudo apt-get update && sudo apt-get install -y fakeroot; then
                    print_success "fakeroot installed successfully ✓"
                else
                    print_error "Failed to install fakeroot. DEB package creation will be skipped."
                    echo "You can manually install fakeroot with: sudo apt-get install fakeroot"
                fi
            else
                print_warning "Skipping fakeroot installation. DEB package creation will be skipped."
                echo "You can manually install fakeroot later with: sudo apt-get install fakeroot"
            fi
        else
            print_success "fakeroot available ✓"
        fi

        print_success "Linux environment checks passed ✓"
    fi
}


# Function to build the project
build_project() {
    print_status "Building project with Maven..."
    cd "$PROJECT_DIR"

    # Clean and package
    mvn clean package -DskipTests

    if [ $? -ne 0 ]; then
        print_error "Maven build failed"
        exit 1
    fi

    print_success "Project built successfully ✓"
}


# Function to create custom runtime with jlink
create_custom_runtime() {
    print_status "Creating custom runtime with jlink..."
    cd "$PROJECT_DIR"

    # Clean and create runtime directory
    rm -rf "$BUILD_DIR/runtime" 2>/dev/null || true
    mkdir -p "$BUILD_DIR"

    # Define required modules for JavaFX application (minimal set)
    REQUIRED_MODULES=(
        "java.base"
        "java.desktop"
        "java.logging"
        "java.net.http"
        "java.prefs"
        "java.xml"
        "jdk.unsupported"
        "javafx.base"
        "javafx.controls"
        "javafx.fxml"
        "javafx.graphics"
    )

    # Add module arguments as a comma-separated string
    MODULE_ARGS=$(IFS=,; echo "${REQUIRED_MODULES[*]}")

    # Create custom runtime
    if jlink \
        --add-modules "$MODULE_ARGS" \
        --strip-debug \
        --compress=2 \
        --no-header-files \
        --no-man-pages \
        --strip-native-commands \
        --output "$BUILD_DIR/runtime" 2>/dev/null; then

        print_success "Custom runtime created ✓"
        return 0
    else
        print_warning "jlink failed, will use system JDK runtime"
    fi
}

# Generic packaging function
package_platform() {
    local platform="$1"

        case "$platform" in
            "Linux")
                ICON="$PACKAGE_DIR/icons/Linux/launcher.png"
                ;;
            "macOS")
                ICON="$PACKAGE_DIR/icons/macOS/launcher.icns"
                ;;
            "Windows")
                ICON="$PACKAGE_DIR/icons/Windows/launcher.ico"
                ;;
        esac

    # Build base jpackage command
    local base_cmd=(
        jpackage
        --dest "$PACKAGE_DIR/distributions"
        --name "$APP_NAME"
        --app-version "$APP_VERSION"
        --main-class "$MAIN_CLASS"
        --main-jar "sdkman-gui-${APP_VERSION}.jar"
        --icon "$ICON"
        --vendor "$VENDOR"
        --description "Modern GUI application for SDKMAN"
        --input "$BUILD_DIR/package-input"
    )

    # Check if custom runtime exists and use it
    if [ -d "$BUILD_DIR/runtime" ] && [ -f "$BUILD_DIR/runtime/release" ]; then
        print_status "Using custom runtime with jlink optimization"
        base_cmd+=(--runtime-image "$BUILD_DIR/runtime")
    else
        print_status "Using system JDK runtime"
    fi

    # Add platform-specific options
    case "$platform" in
        "Linux")
            base_cmd+=(
                --linux-menu-group "Development"
                --linux-package-name "${PACKAGE_NAME}"
                --linux-deb-maintainer "$VENDOR"
                --linux-shortcut
            )
            ;;
        "macOS")
            base_cmd+=(
              --mac-package-name "${PACKAGE_NAME}"
              --mac-package-identifier "$APP_ID"
              --mac-app-category "developer-tools"
            )
            ;;
        "Windows")
            base_cmd+=(
                --win-menu
                --win-shortcut
                --win-dir-chooser
                --win-menu-group "Development"
            )
            ;;
    esac

    # Execute jpackage command
    "${base_cmd[@]}"

    if [ $? -eq 0 ]; then
        print_success "$platform package created ✓"
    else
        print_error "Failed to create $platform package"
        return 1
    fi
}

# Function to package for Linux
package_linux() {
    package_platform "Linux"
}

# Function to package for macOS
package_macos() {
    package_platform "macOS"
}

# Function to package for Windows (WSL)
package_windows() {
    package_platform "Windows"
}

# Function to rename Linux package
rename_linux_package() {
    print_status "Renaming Linux package..."

    local dist_dir="$PACKAGE_DIR/distributions"
    local expected_name="${PACKAGE_NAME}_${APP_VERSION}-1_amd64.deb"
    local desired_name="${PACKAGE_NAME}-${APP_VERSION}-${ARCH}.deb"

    if [ -f "$dist_dir/$expected_name" ]; then
        mv "$dist_dir/$expected_name" "$dist_dir/$desired_name"
        print_success "Renamed to: $desired_name ✓"
    else
        print_warning "Expected package not found: $expected_name"
        print_status "Available files in distributions directory:"
        ls -la "$dist_dir/"

        # Try to find any .deb file and rename it
        local found_deb
        found_deb=$(find "$dist_dir" -name "*.deb" -type f | head -1)
        if [ -n "$found_deb" ]; then
            local filename
            filename=$(basename "$found_deb")
            mv "$found_deb" "$dist_dir/$desired_name"
            print_success "Found and renamed $filename to: $desired_name ✓"
        fi
    fi
}

# Function to rename macOS package
rename_macos_package() {
    print_status "Renaming macOS package..."

    local dist_dir="$PACKAGE_DIR/distributions"
    local expected_name="${APP_NAME}-${APP_VERSION}.dmg"
    local desired_name="${PACKAGE_NAME}-${APP_VERSION}-${ARCH}.dmg"

    if [ -f "$dist_dir/$expected_name" ]; then
        mv "$dist_dir/$expected_name" "$dist_dir/$desired_name"
        print_success "Renamed to: $desired_name ✓"
    else
        print_warning "Expected package not found: $expected_name"
        print_status "Available files in distributions directory:"
        ls -la "$dist_dir/"

        # Try to find any .dmg file and rename it
        local found_dmg=$(find "$dist_dir" -name "*.dmg" -type f | head -1)
        if [ -n "$found_dmg" ]; then
            local filename=$(basename "$found_dmg")
            mv "$found_dmg" "$dist_dir/$desired_name"
            print_success "Found and renamed $filename to: $desired_name ✓"
        fi
    fi
}

# Function to prepare packaging input directory
prepare_package_input() {
    print_status "Preparing packaging input directory..."

    # Create a clean input directory for jpackage
    mkdir -p "$BUILD_DIR/package-input"

    # Copy only the main JAR file
    if [ -f "$TARGET_DIR/sdkman-gui-${APP_VERSION}.jar" ]; then
        cp "$TARGET_DIR/sdkman-gui-${APP_VERSION}.jar" "$BUILD_DIR/package-input/"
        print_success "Prepared JAR file for packaging ✓"
    else
        print_error "Main JAR file not found: $TARGET_DIR/sdkman-gui-${APP_VERSION}.jar"
        return 1
    fi
}

# Main packaging function
package_app() {
    detect_platform
    check_requirements

    # Clean previous builds
    rm -rf "$BUILD_DIR"

    # Build project
    build_project

    # Create custom runtime with jlink
    create_custom_runtime || true

    # Prepare packaging input directory (only necessary files)
    prepare_package_input
    if [ $? -ne 0 ]; then
        print_error "Failed to prepare packaging input"
        exit 1
    fi

    # Package based on platform
    case "$PLATFORM" in
        linux)
            package_linux
            # Rename Linux package if needed
            rename_linux_package
            ;;
        macos)
            package_macos
            # Rename macOS package if needed
            rename_macos_package
            ;;
        windows)
            package_windows
            ;;
        *)
            print_warning "Unknown platform. Creating portable distribution only."
            ;;
    esac

    # Show results
    print_success "Packaging completed!"
}

# Main execution
print_status "Starting SDKMAN GUI packaging..."

package_app