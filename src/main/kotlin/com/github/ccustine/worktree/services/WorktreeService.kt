package com.github.ccustine.worktree.services

import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Service for managing git worktrees within a project.
 */
interface WorktreeService {

    /**
     * Lists all worktrees for the current repository.
     * May block to refresh the cache if needed.
     */
    fun listWorktrees(): List<WorktreeInfo>

    /**
     * Returns cached worktree list without blocking.
     * Returns null if no cache is available yet.
     * Use this for UI code that cannot block.
     */
    fun getCachedWorktrees(): List<WorktreeInfo>?

    /**
     * Creates a new worktree.
     *
     * @param branch The branch to checkout in the new worktree
     * @param path The path where the worktree will be created
     * @param createBranch If true, creates a new branch; if false, uses existing branch
     * @return The created WorktreeInfo, or null if creation failed
     */
    fun createWorktree(branch: String, path: Path, createBranch: Boolean): Result<WorktreeInfo>

    /**
     * Removes a worktree.
     *
     * @param path The path of the worktree to remove
     * @param force If true, removes even if there are uncommitted changes
     * @return Success or failure with error message
     */
    fun removeWorktree(path: Path, force: Boolean = false): Result<Unit>

    /**
     * Gets information about a specific worktree.
     */
    fun getWorktreeInfo(path: Path): WorktreeInfo?

    /**
     * Checks if the current project is a worktree (not the main working tree).
     */
    fun isWorktree(): Boolean

    /**
     * Gets the main repository path (the parent of all worktrees).
     */
    fun getMainRepositoryPath(): Path?

    /**
     * Gets the default path for new worktrees.
     */
    fun getDefaultWorktreePath(branchName: String): Path?

    /**
     * Checks if a worktree has uncommitted changes.
     */
    fun hasUncommittedChanges(path: Path): Boolean

    /**
     * Checks if a worktree has unpushed commits.
     */
    fun hasUnpushedCommits(path: Path): Boolean

    companion object {
        fun getInstance(project: Project): WorktreeService =
            project.getService(WorktreeService::class.java)
    }
}
