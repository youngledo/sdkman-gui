# Release Guide

This document describes how to create a new release of SDKMAN GUI and publish it across multiple platforms.

## Prerequisites

1. Push all changes to the `master` branch
2. Ensure all tests pass
3. Update version in `pom.xml` if needed
4. Test builds on all platforms (optional but recommended)

## Release Steps

### 1. Create a Git Tag

```bash
# Set the version (e.g., 1.0.0)
VERSION="1.0.0"

# Ensure all changes are committed
git add .
git commit -m "chore: prepare for v${VERSION} release"
git push origin master

# Create and push the tag
git tag -a "v${VERSION}" -m "Release version ${VERSION}"
git push origin "v${VERSION}"
```

### 2. GitHub Actions Build

Once you push the tag, GitHub Actions will automatically build packages for all platforms:

#### macOS
- **macos-13** (Intel x86_64): `sdkman-gui_${VERSION}_x86_64.dmg`
- **macos-14** (Apple Silicon arm64): `sdkman-gui_${VERSION}_arm64.dmg`

#### Windows
- **windows-latest** (x64): `sdkman-gui_${VERSION}_x86_64.exe`

#### Linux
- **ubuntu-latest** (x64):
  - `sdkman-gui_${VERSION}_x86_64.deb` (Debian/Ubuntu)
  - `sdkman-gui_${VERSION}_x86_64.rpm` (Fedora/RHEL)

Monitor the workflow at: https://github.com/youngledo/sdkman-gui/actions

### 3. Update Homebrew Cask (macOS only)

After the GitHub Release is published:

```bash
# Navigate to the Homebrew tap repository
cd /Users/huangxiao/IdeaProjects/open-source/homebrew-sdkman-gui

# Run the update script (downloads DMGs and calculates SHA256)
./update-cask.sh ${VERSION}

# Review changes
git diff Casks/sdkman-gui.rb

# Commit and push
git add Casks/sdkman-gui.rb
git commit -m "chore: update to version ${VERSION}"
git push origin main
```

### 4. Verify Installations

#### macOS (Homebrew)
```bash
# Uninstall previous version
brew uninstall --cask sdkman-gui

# Update tap
brew update

# Install new version
brew install --cask youngledo/sdkman-gui/sdkman-gui

# Launch and verify
open -a "SDKMAN GUI"
```

#### Windows
```powershell
# Download the installer
Invoke-WebRequest -Uri "https://github.com/youngledo/sdkman-gui/releases/download/v${VERSION}/sdkman-gui_${VERSION}_x86_64.exe" -OutFile "sdkman-gui_${VERSION}_x86_64.exe"

# Run the installer
.\sdkman-gui_${VERSION}_x86_64.exe

# Launch from Start Menu
```

#### Linux (Debian/Ubuntu)
```bash
# Download and install
wget https://github.com/youngledo/sdkman-gui/releases/download/v${VERSION}/sdkman-gui_${VERSION}_x86_64.deb
sudo dpkg -i sdkman-gui_${VERSION}_x86_64.deb

# Launch
sdkman-gui
```

#### Linux (Fedora/RHEL)
```bash
# Download and install
wget https://github.com/youngledo/sdkman-gui/releases/download/v${VERSION}/sdkman-gui_${VERSION}_x86_64.rpm
sudo rpm -i sdkman-gui_${VERSION}_x86_64.rpm

# Launch
sdkman-gui
```

### 5. Announce Release

Share the release:
- Update README.md if needed
- Create announcement in GitHub Discussions
- Post on social media (optional)
- Update project documentation

## Manual Build (Fallback)

If GitHub Actions fails, you can build manually on each platform:

### macOS
```bash
cd package
./package.sh
# Output: target/distributions/sdkman-gui_${VERSION}_${ARCH}.dmg
```

### Windows
```powershell
mvn clean package jpackage:jpackage -DskipTests
# Output: target/distributions/SDKMAN GUI-${VERSION}.exe
```

### Linux
```bash
mvn clean package jpackage:jpackage -DskipTests
# Output: target/distributions/*.deb and *.rpm
```

## Expected Build Artifacts

After a successful release, you should have these files on GitHub:

```
sdkman-gui_${VERSION}_arm64.dmg      # macOS Apple Silicon
sdkman-gui_${VERSION}_x86_64.dmg     # macOS Intel
sdkman-gui_${VERSION}_x86_64.exe     # Windows
sdkman-gui_${VERSION}_x86_64.deb     # Debian/Ubuntu
sdkman-gui_${VERSION}_x86_64.rpm     # Fedora/RHEL
```

## Troubleshooting

### Build Fails on GitHub Actions

**macOS:**
- Check JDK 21 availability
- Verify package.sh has execute permissions
- Check for macOS-specific dependencies

**Windows:**
- Ensure WiX Toolset is available (built into windows-latest runner)
- Check icon file path: `package/icons/Windows/launcher.ico`
- Verify Maven can access Windows-specific plugins

**Linux:**
- Check for missing system dependencies
- Verify icon file path: `package/icons/Linux/launcher.png`
- Test both .deb and .rpm generation

### Installation Fails

**macOS:**
- Verify DMG is not corrupted: `hdiutil verify sdkman-gui_${VERSION}_*.dmg`
- Check if app is quarantined: `xattr -d com.apple.quarantine /Applications/SDKMAN\ GUI.app`

**Windows:**
- Run installer as Administrator
- Check Windows Defender/antivirus settings
- Verify .exe signature (if signed)

**Linux:**
- Check dependency requirements: `dpkg -I sdkman-gui_${VERSION}_*.deb`
- For Ubuntu/Debian: Install missing deps with `sudo apt-get install -f`
- For Fedora/RHEL: Install missing deps with `sudo dnf install`

### Architecture-specific Issues

**Verify architecture naming:**
- macOS: `arm64` (Apple Silicon), `x86_64` (Intel)
- Windows: `x86_64` (64-bit Intel/AMD)
- Linux: `x86_64` (64-bit Intel/AMD)

**Check file naming convention:**
```
sdkman-gui_${VERSION}_${ARCH}.${EXTENSION}
```

## Platform-specific Notes

### macOS
- Requires macOS Big Sur (11.0) or later
- App is signed with ad-hoc signature (for Gatekeeper)
- DMG includes both app bundle and symbolic link to /Applications

### Windows
- Requires Windows 10 or later
- Creates Start Menu shortcuts automatically
- Installer supports silent mode: `/S`
- Uninstaller available in Control Panel

### Linux
- .deb package for Debian/Ubuntu-based distributions
- .rpm package for Fedora/RHEL-based distributions
- Desktop entry created automatically
- Icon installed to `/usr/share/icons/hicolor/`

## Rollback Procedure

If you need to rollback a release:

```bash
# 1. Delete the tag locally and remotely
git tag -d v${VERSION}
git push origin :refs/tags/v${VERSION}

# 2. Delete the GitHub Release
# Go to https://github.com/youngledo/sdkman-gui/releases
# Delete the release manually

# 3. Revert Homebrew Cask (if published)
cd /Users/huangxiao/IdeaProjects/open-source/homebrew-sdkman-gui
git revert HEAD
git push origin main
```

## Post-release Checklist

- [ ] All platform builds succeeded
- [ ] All installers tested on target platforms
- [ ] GitHub Release created with all artifacts
- [ ] Homebrew Cask updated (macOS)
- [ ] README.md updated with installation instructions
- [ ] Release announcement published
- [ ] Documentation updated if needed
