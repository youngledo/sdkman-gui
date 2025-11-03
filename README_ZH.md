# SDKMAN GUI

[English](README.md) | **中文**

> 现代化的[SDKMAN](https://github.com/sdkman)图形化管理工具，提供类似[Applite](https://github.com/milanvarady/Applite)的用户体验。

基于JavaFX 25 + Maven 4.0开发，参考Applite设计风格，为SDKMAN提供优雅的GUI界面。

## 🎬 演示

<img src="docs/images/home.png" alt="home">
<img src="docs/images/jdk.png" alt="jdk">
<img src="docs/images/sdk.png" alt="sdk">
<img src="docs/images/settings.png" alt="settings">

**[📹 观看此视频 (sdkman-gui.webm)](sdkman-gui.webm)**


## ✨ 特性

- 🎨 **现代化UI** - 基于AtlantaFX主题，提供精美的界面设计
- 🌍 **国际化支持** - 支持中英文，自动检测系统语言
- 🌗 **主题切换** - 支持亮色/暗色主题
- 📦 **SDK管理** - 浏览、安装、卸载、切换SDK版本
- 🔍 **搜索过滤** - 快速查找所需的SDK
- 🏷️ **分类浏览** - 按类别查看SDK（Java、构建工具、编程语言等）
- 🔄 **更新检查** - 自动检测SDK更新
- ⚙️ **配置管理** - 灵活的应用配置

## 🛠️ 技术栈

| 组件        | 版本 | 说明     |
|-----------|------|--------|
| Java      | 25.0.1 | 运行环境   |
| JavaFX    | 25.0.1 | UI框架   |
| Maven     | 4.0 | 构建工具   |
| AtlantaFX | 2.1.0 | UI主题库  |
| Jackson   | 2.18.2 | JSON处理 |
| Log4j2    | 2.21.1 | 日志框架   |
| Apache Commons Exec | 1.5.0 | 进程执行    |

## 📋 前置要求

- JDK 25或更高版本
- Maven 4.0或更高版本
- SDKMAN已安装（~/.sdkman目录存在）

## 🚀 快速开始

### 克隆项目

```bash
git clone <repository-url>
cd sdkgui
```

### 编译项目

```bash
mvn clean compile
```

### 运行应用

```bash
mvn javafx:run
```

### 打包应用

```bash
# 创建可执行JAR
mvn clean package

# 运行打包后的JAR
java -jar target/sdkman-gui-1.0.0.jar
```

## 🌍 国际化

应用支持以下语言：

- 🇺🇸 English（英文）
- 🇨🇳 简体中文

语言会根据系统设置自动选择，也可以在设置页面手动切换。

## 🎨 主题

支持三种主题模式：

- **亮色主题**（Light）- 明亮清爽
- **暗色主题**（Dark）- 护眼舒适
- **自动模式**（Auto）- 跟随系统设置

## 📝 使用说明

### 发现SDK

1. 打开应用后，默认进入"发现"页面
2. 浏览可用的SDK列表
3. 使用分类筛选或搜索功能快速定位
4. 点击"安装"按钮即可安装SDK

### 管理已安装的SDK

1. 切换到"已安装"标签页
2. 查看所有已安装的SDK
3. 可以：
   - 设置默认版本
   - 更新到最新版本
   - 卸载不需要的版本

### 配置应用

1. 切换到"设置"标签页
2. 可配置：
   - 界面主题
   - 显示语言
   - 自动更新检查
   - SDKMAN路径

## 🔧 配置文件

应用配置保存在：`~/.sdkman-gui/config.json`

配置示例：

```json
{
  "language": "zh_CN",
  "theme": "light",
  "autoUpdate": true,
  "sdkmanPath": "/Users/username/.sdkman"
}
```

## 🐛 故障排除

### 无法启动应用

确保已安装JDK 25：
```bash
java -version
```

### 无法找到SDKMAN

检查SDKMAN是否已安装：
```bash
ls ~/.sdkman
```

如果未安装，请访问：https://sdkman.io/install

### Maven构建失败

确保使用Maven 4.0：
```bash
mvn -version
```

## 📚 文档

- [JavaFX 25文档](https://openjfx.io/javadoc/25/)
- [AtlantaFX文档](https://mkpaz.github.io/atlantafx/)
- [SDKMAN文档](https://sdkman.io/usage/)

## 📄 许可证

MIT License

## 🙏 致谢

- [SDKMAN](https://sdkman.io/) - 优秀的SDK管理工具
- [AtlantaFX](https://github.com/mkpaz/atlantafx) - 精美的JavaFX主题库
- [Applite](https://github.com/milanvarady/Applite) - UI设计灵感来源
