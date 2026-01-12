# Git Worktree Manager - IntelliJ Plugin Design

## Overview

A JetBrains IntelliJ IDEA plugin for managing git worktrees from within the IDE. Enables creating, opening, switching between, and removing worktrees with full integration into the Git menu.

## Use Cases

- **Feature branch isolation** - Work on multiple features simultaneously without stashing
- **Long-running branch maintenance** - Maintain worktrees for stable branches alongside feature work
- **Code review workflow** - Spin up temporary worktrees to review PRs while keeping main work untouched

## Technology Stack

- **Language:** Kotlin
- **Build:** Gradle with `org.jetbrains.intellij.platform` plugin 2.x
- **Target:** IntelliJ IDEA 2025.1+
- **Plugin ID:** `org.metastacks.worktree-manager`

## Project Structure

```
worktree_idea/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/
│   │   └── org/metastacks/worktree/
│   │       ├── actions/         # Menu actions
│   │       ├── services/        # Git worktree operations
│   │       ├── ui/              # Dialogs
│   │       └── util/            # Helpers
│   └── resources/
│       └── META-INF/
│           └── plugin.xml       # Plugin descriptor
└── src/test/kotlin/
```

## Core Services

### WorktreeService (per-project singleton)

Central service wrapping git worktree commands:

| Method | Description |
|--------|-------------|
| `listWorktrees(): List<WorktreeInfo>` | Parses `git worktree list --porcelain` |
| `createWorktree(branch, path, createBranch)` | Runs `git worktree add [-b] <path> <branch>` |
| `removeWorktree(path, force)` | Runs `git worktree remove [--force] <path>` |
| `getWorktreeInfo(path): WorktreeInfo?` | Returns info for a specific worktree |
| `isWorktree(): Boolean` | Checks if current project is a worktree |

### WorktreeInfo (data class)

```kotlin
data class WorktreeInfo(
    val path: Path,
    val branch: String?,
    val commitHash: String,
    val isMain: Boolean,
    val isDirty: Boolean,
    val hasUnpushedCommits: Boolean
)
```

### WorktreeWindowService (application-level singleton)

Tracks which worktrees are open across all IDEA windows:
- Registers/unregisters projects on open/close
- Provides "is this worktree open elsewhere?" check for safety

## Menu Structure

Added to VCS → Git menu:

```
Git
├── ... (existing items)
├── ─────────────────────
├── Create Worktree...          → CreateWorktreeAction
├── Open Worktree...            → OpenWorktreeAction
├── Remove Worktree...          → RemoveWorktreeAction
└── Switch to Worktree         → (submenu, dynamically populated)
    ├── main                    → SwitchToWorktreeAction
    ├── feature-login           → SwitchToWorktreeAction
    └── bugfix-header           → SwitchToWorktreeAction
```

### Action Behaviors

| Action | Behavior |
|--------|----------|
| Create Worktree | Opens dialog: branch selector + path field (defaults to `.worktrees/<branch-name>`) |
| Open Worktree | Opens dialog listing all worktrees with Open button |
| Remove Worktree | Opens dialog listing worktrees with status indicators. Confirms before removal. |
| Switch to Worktree | Submenu shows sibling worktrees. Click opens respecting IDEA's window preference. |

### Visibility Rules

- All actions visible only when project has Git repository
- "Switch to Worktree" submenu hidden if no sibling worktrees exist
- Current worktree grayed out in Switch submenu

## Dialog Designs

### Create Worktree Dialog

```
┌─────────────────────────────────────────────────────┐
│ Create Worktree                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Branch:  ○ Existing branch  ○ Create new branch    │
│                                                     │
│           [feature/login         ▼]  (dropdown)     │
│                                                     │
│  Path:    [.worktrees/feature-login    ] [Browse]   │
│                                                     │
│           ☑ Open in IDE after creation              │
│                                                     │
├─────────────────────────────────────────────────────┤
│                         [Cancel]  [Create]          │
└─────────────────────────────────────────────────────┘
```

### Open / Remove Worktree Dialog

```
┌─────────────────────────────────────────────────────┐
│ Open Worktree                                       │
├─────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────┐ │
│ │ Branch            Path                   Status │ │
│ ├─────────────────────────────────────────────────┤ │
│ │ feature-login     .worktrees/feature-login   ●  │ │
│ │ bugfix-header     .worktrees/bugfix-header      │ │
│ │ main              /code/myproject          MAIN │ │
│ └─────────────────────────────────────────────────┘ │
│                                                     │
│  ● = currently open in another window               │
├─────────────────────────────────────────────────────┤
│                         [Cancel]  [Open]            │
└─────────────────────────────────────────────────────┘
```

Remove dialog adds status icons for dirty/unpushed and requires confirmation.

## Project Name Decoration

When a project is a worktree, the project view shows:

```
Project View
├── myproject [worktree: feature-login]    ← decorated name
│   ├── src/
│   ├── build.gradle.kts
│   └── ...
```

Main clone shows no decoration.

## Worktree Storage

New worktrees created in `.worktrees/` subdirectory of main clone:
- Path: `.worktrees/<branch-name>`
- Can be gitignored
- Keeps filesystem organized

## Error Handling

| Scenario | Response |
|----------|----------|
| Git not available | Disable all actions, show "Git not configured" if clicked |
| Not a git repository | Hide worktree menu items entirely |
| Worktree creation fails | Show error dialog with git stderr message |
| Path already exists | Validate in dialog, disable Create button, show inline error |
| Remove with uncommitted changes | Show warning dialog listing changes, require explicit confirmation |
| Remove with unpushed commits | Show warning with commit count, require confirmation |
| Worktree open in another window | Block removal, show "Close the worktree project first" message |
| Branch already has worktree | Validate in dialog, show "Branch already checked out in: /path" |

## Opening Worktrees

When opening a worktree in a new window:
- Respect IDEA's existing "Open project in new window" user preference
- Use platform's `ProjectManager` API

## Implementation Phases

### Phase 1 - Foundation (MVP)
- Project setup with Gradle + IntelliJ Platform Plugin
- `WorktreeService` with core git operations
- Basic `plugin.xml` registration

### Phase 2 - Menu Actions
- Create Worktree action + dialog
- Open Worktree action + dialog
- Remove Worktree action + dialog with safety checks
- Switch to Worktree submenu

### Phase 3 - Polish
- Project name decoration
- `WorktreeWindowService` for tracking open worktrees
- Proper error handling and validation

### Future (post-MVP)
- Status bar widget for quick switching
- Tool window with full worktree management
- Keyboard shortcuts
- Notifications for worktree events
