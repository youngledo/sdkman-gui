# SDKMAN GUI

For the native version, please refer to: [sdkman-gui-native](https://github.com/youngledo/sdkman-gui-native).

**English** | [中文](README_ZH.md)

> A modern graphical management tool for [SDKMAN](https://github.com/sdkman).

Cross-platform, supports Windows, macOS, Ubuntu. Built with JavaFX + Maven, offering an elegant GUI interface for SDKMAN.

## 🎬 Demo

<img src="docs/images/home.png" alt="home">
<img src="docs/images/jdk.png" alt="jdk">
<img src="docs/images/sdk.png" alt="sdk">
<img src="docs/images/settings.png" alt="settings">

**[📹 Watch Demo Video (sdkman-gui.webm)](https://youtu.be/gbxEjiw3i-o)**

## ✨ Features

- 💻 **Cross-platform** - supports Windows, macOS, Ubuntu
- 🎨 **Modern UI** - Beautiful interface design based on AtlantaFX themes
- 🌍 **Internationalization** - Support for English and Chinese with automatic system language detection
- 🌗 **Theme Switching** - Support for light/dark themes
- 📦 **SDK Management** - Browse, install, uninstall, and switch SDK versions
- 🔍 **Search & Filter** - Quickly find the SDKs you need
- 🏷️ **Category Browsing** - View SDKs by category (Java, Build Tools, Programming Languages, etc.)
- 🔄 **Update Checking** - Automatically detect SDK updates
- ⚙️ **Configuration Management** - Flexible application configuration

## 📦 Installation

### macOS

**Manual Installation:**
Download the DMG file for your architecture from [Releases](https://github.com/youngledo/sdkman-gui/releases):
- Apple Silicon: `sdkman-gui_*_arm64.dmg`

**Homebrew:**
```bash
brew tap youngledo/sdkman-gui
brew install --cask sdkman-gui
```

### Windows

Download and run the installer from [Releases](https://github.com/youngledo/sdkman-gui/releases):
- `sdkman-gui_*_x86_64.exe`

### Linux

**Debian/Ubuntu:**
```bash
# Download the .deb package from releases
wget https://github.com/youngledo/sdkman-gui/releases/download/v1.0.0/sdkman-gui_1.0.0_x86_64.deb
sudo dpkg -i sdkman-gui_1.0.0_x86_64.deb
```

**Fedora/RHEL:**
```bash
# Download the .rpm package from releases
wget https://github.com/youngledo/sdkman-gui/releases/download/v1.0.0/sdkman-gui_1.0.0_x86_64.rpm
sudo rpm -i sdkman-gui_1.0.0_x86_64.rpm
```

### Prerequisites

⚠️ **SDKMAN must be installed first:**
```bash
curl -s "https://get.sdkman.io" | bash
```

## 🌍 Internationalization

The application supports the following languages:

- 🇺🇸 English
- 🇨🇳 Simplified Chinese

Language is automatically selected based on system settings, but can also be manually switched in the settings page.

## 🎨 Themes

Three theme modes are supported:

- **Light Theme** - Bright and refreshing
- **Dark Theme** - Eye-friendly and comfortable
- **Auto Mode** - Follows system settings

## 📝 Usage Guide

### Discovering SDKs

1. Open the application, default landing on the "Home" page
2. Browse the available SDK list
3. Use category filters or search functionality to quickly locate SDKs
4. Click "Install" button to install an SDK

### Managing Installed SDKs

1. Navigate to the "JDK" or "SDK" page
2. View all installed SDKs and versions
3. You can:
   - Set default versions
   - Install new versions
   - Uninstall unwanted versions
   - Switch between versions

### SDK Details Management

1. Click on any SDK to view detailed information
2. Browse all available versions
3. Manage individual versions:
   - Install specific versions
   - Uninstall versions
   - Set versions as default
   - View installation status and progress

### Configuring the Application

1. Navigate to the "Settings" page
2. You can configure:
   - Interface theme
   - Display language
   - SDKMAN installation path

## 🔧 Configuration File

Application configuration is saved in: `~/.sdkman-gui/config.json`

Configuration example:

```json
{
  "language": "en_US",
  "theme": "light",
  "autoUpdate": true,
  "sdkmanPath": "/Users/username/.sdkman"
}
```

## 📄 License

MIT License

## 🙏 Acknowledgments

- [SDKMAN](https://sdkman.io/) - Excellent SDK management tool
- [AtlantaFX](https://github.com/mkpaz/atlantafx) - Beautiful JavaFX theme library
- [IKonli](https://github.com/kordamp/ikonli) - Beautiful JavaFX icon library
