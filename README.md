# Worktree Manager

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Manage Git worktrees directly from IntelliJ IDEA.

## Features

- **Create Worktrees** - Create new worktrees from existing or new branches with customizable paths
- **Open Worktrees** - Open worktrees in new IDE windows with status indicators
- **Switch Between Worktrees** - Quick submenu to jump between worktree projects
- **Remove Worktrees** - Safe removal with real-time dirty/unpushed commit warnings
- **Project Indicator** - See which worktree you're in via project name decoration
- **Folder Decorations** - Worktree directories display a green branch icon with branch name
- **Context Menu Actions** - Right-click worktree folders to open or remove them directly

## Installation

### From JetBrains Marketplace

1. Open **Settings** → **Plugins** → **Marketplace**
2. Search for "Worktree Manager"
3. Click **Install**

### Manual Installation

1. Download the latest release from [Releases](https://github.com/metastacks/worktree-manager/releases)
2. Open **Settings** → **Plugins** → **Install Plugin from Disk...**
3. Select the downloaded `.zip` file

## Usage

### Git Menu

Access worktree actions from **Git** → **Worktrees**:

- **Create Worktree...** - Opens dialog to create a new worktree
- **Open Worktree...** - Lists existing worktrees to open in a new window
- **Remove Worktree...** - Safely remove worktrees with status checks
- **Switch to Worktree** - Submenu showing sibling worktrees for quick switching

### Context Menu

Right-click on worktree folders in the Project view:

- **Open Worktree in New Window** - Opens the worktree in a new IDE window
- **Remove Worktree** - Removes the worktree with safety checks

### Visual Indicators

- Worktree directories show a green branch icon with the branch name
- Project root displays `[worktree: branch-name]` when opened as a worktree

### Settings

Configure plugin behavior in **Settings** → **Version Control** → **Worktree Manager**.

## Building from Source

```bash
# Build the plugin
./gradlew build

# Run in sandbox IDE for testing
./gradlew runIde

# Run tests
./gradlew test
```

## Requirements

- IntelliJ IDEA 2025.3 or later
- Git repository with worktree support

## License

Apache 2.0 - see [LICENSE](LICENSE) for details.
