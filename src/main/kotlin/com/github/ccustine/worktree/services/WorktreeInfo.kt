package com.github.ccustine.worktree.services

import java.nio.file.Path

/**
 * Information about a git worktree.
 */
data class WorktreeInfo(
    val path: Path,
    val branch: String?,
    val commitHash: String,
    val isMain: Boolean,
    val isDirty: Boolean = false,
    val hasUnpushedCommits: Boolean = false
) {
    /**
     * Display name for the worktree (branch name or path basename).
     */
    val displayName: String
        get() = branch ?: path.fileName.toString()

    /**
     * Short commit hash for display.
     */
    val shortHash: String
        get() = commitHash.take(7)
}
