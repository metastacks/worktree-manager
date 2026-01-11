# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IntelliJ IDEA plugin for managing git worktrees from within the IDE. Enables creating, opening, switching between, and removing worktrees with full integration into the Git menu.

## Build Commands

```bash
# Build the plugin
./gradlew build

# Run plugin in sandbox IDE instance
./gradlew runIde

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Verify plugin compatibility
./gradlew verifyPlugin
```

## Architecture

### Service Layer (project-scoped and app-scoped singletons)

- **WorktreeService** (`services/WorktreeService.kt`) - Project-level service interface wrapping git worktree CLI commands. Use `WorktreeService.getInstance(project)` to obtain.
- **WorktreeServiceImpl** - Implementation using Git4Idea's `Git.runCommand()` with `GitLineHandler`. Parses `git worktree list --porcelain` output. Includes caching with 5-second TTL and background thread execution to avoid blocking EDT.
- **WorktreeWindowService** - Application-level service tracking which worktrees are open across all IDE windows.

### Actions (`actions/`)

Menu actions registered in `plugin.xml` under `Git.MainMenu`:
- **CreateWorktreeAction** - Opens dialog, calls `WorktreeService.createWorktree()`
- **OpenWorktreeAction** - Lists worktrees, opens selected in new window
- **RemoveWorktreeAction** - Removes worktree with safety checks for dirty/unpushed state
- **SwitchWorktreeActionGroup** - Dynamic submenu of sibling worktrees

### UI (`ui/`)

- **CreateWorktreeDialog** - Branch selection + path + options
- **WorktreeListDialog** - Table of worktrees with status indicators
- **WorktreeProjectNameDecorator** - Adds `[worktree: branch-name]` to project view

### Data Model

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

### Plugin Registration

All services and actions registered in `src/main/resources/META-INF/plugin.xml`. Plugin depends on `Git4Idea` bundled plugin for git operations.

## Key Patterns

- Actions check `GitRepositoryManager.getInstance(project).repositories.isNotEmpty()` for visibility
- Git commands run via Git4Idea's `Git.getInstance().runCommand()` with `GitLineHandler` and `GitCommand` constants (e.g., `GitCommand.WORKTREE`, `GitCommand.STATUS`)
- New worktrees default to `.worktrees/<sanitized-branch-name>` under main repo
- Services obtained via `project.getService()` or companion object helpers
- Threading: Use `ApplicationManager.getApplication().executeOnPooledThread()` to avoid blocking EDT/ReadAction when running git commands
- Caching: WorktreeServiceImpl caches worktree list with TTL to avoid repeated blocking calls during UI updates

## Target Platform

- IntelliJ IDEA 2025.3+ (sinceBuild 253)
- JDK 21 (configured via Kotlin jvmToolchain - must match IntelliJ's bundled JDK)
- Kotlin 2.3.0
- Gradle 9.2.1 with `org.jetbrains.intellij.platform` plugin 2.10.5
- Foojay toolchain resolver 1.0.0 for JDK auto-provisioning
