# Codex UI - IntelliJ Plugin

[![Version](https://img.shields.io/badge/version-1.1.18-blue.svg)](https://github.com/ichaly/codex-launcher/releases)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-orange.svg)](https://www.jetbrains.com/idea/)

<img width="800" alt="The screenshot of Codex UI." src="https://github.com/user-attachments/assets/4ee3fbd8-e384-4672-94c6-e4e9041a8e0d" />

Codex UI is an **unofficial** IntelliJ IDEA plugin that keeps the OpenAI Codex CLI one click away inside the IDE.

> **Important:** Install the [OpenAI Codex CLI](https://github.com/openai/codex) separately before using this plugin.

> **For Windows users:** Please select your terminal shell in the plugin settings to ensure proper functionality. Go to _Settings (→ Other Settings) → Codex UI_.

## ✨ Features

- **One-click launch** from the toolbar or Tools menu
- **Integrated terminal** that opens a dedicated "Codex UI" tab in the project or selected module
- **Completion notifications** after Codex CLI finishes processing the current run
- **Automatic file opening** for files updated by Codex
- **Built-in MCP server pairing** with guided setup for IntelliJ's MCP server (2025.2+)
- **Flexible configuration** for models, working directories, and notifications

## 🛠️ Installation

### Prerequisites
- IntelliJ IDEA 2024.2 or later (or other compatible JetBrains IDEs)
- OpenAI Codex CLI installed and available in your system PATH

## 🚀 Usage

### Quick Start
1. Click the **Launch Codex UI** button in the main toolbar.
2. Or choose **Tools** → **Launch Codex UI**.
3. The integrated terminal opens a new "Codex UI" tab and runs `codex` automatically.

### Configuration
Open **Settings (→ Other Settings) → Codex UI** to pick the model, module working directory behavior, notifications, and auto-open options.

## 📝 Development

### Building from Source
```bash
git clone https://github.com/ichaly/codex-launcher.git
cd codex-launcher
./gradlew buildPlugin
```

Release maintainers should follow [the publishing guide](docs/PUBLISHING.md).

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
