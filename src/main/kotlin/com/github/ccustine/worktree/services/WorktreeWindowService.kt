package com.github.ccustine.worktree.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Application-level service for tracking which worktrees are open in IDE windows.
 */
interface WorktreeWindowService {

    /**
     * Registers a project as an open worktree.
     */
    fun registerOpenWorktree(path: Path, project: Project)

    /**
     * Unregisters a project when it closes.
     */
    fun unregisterWorktree(path: Path)

    /**
     * Checks if a worktree is currently open in another window.
     */
    fun isWorktreeOpen(path: Path): Boolean

    /**
     * Gets the project for an open worktree, if any.
     */
    fun getOpenWorktreeProject(path: Path): Project?

    /**
     * Gets all currently open worktree paths.
     */
    fun getOpenWorktrees(): Set<Path>

    companion object {
        fun getInstance(): WorktreeWindowService =
            ApplicationManager.getApplication().getService(WorktreeWindowService::class.java)
    }
}
