package com.github.ccustine.worktree.actions

import com.github.ccustine.worktree.services.WorktreeInfo
import com.github.ccustine.worktree.services.WorktreeService
import com.github.ccustine.worktree.services.WorktreeWindowService
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

/**
 * Dynamic action group that shows all available worktrees for switching.
 */
class SwitchWorktreeActionGroup : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()

        if (!hasGitRepository(project)) {
            return emptyArray()
        }

        val service = WorktreeService.getInstance(project)
        val worktrees = service.listWorktrees()

        if (worktrees.size <= 1) {
            return emptyArray()
        }

        val currentPath = project.basePath?.let { Path.of(it) }

        return worktrees.map { worktree ->
            SwitchToWorktreeAction(worktree, isCurrent = worktree.path == currentPath)
        }.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val hasRepo = project != null && hasGitRepository(project)

        if (hasRepo) {
            val service = WorktreeService.getInstance(project!!)
            val worktrees = service.listWorktrees()
            e.presentation.isEnabledAndVisible = worktrees.size > 1
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}

/**
 * Action to switch to a specific worktree.
 */
class SwitchToWorktreeAction(
    private val worktree: WorktreeInfo,
    private val isCurrent: Boolean
) : AnAction(worktree.displayName), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        if (isCurrent) return

        val windowService = WorktreeWindowService.getInstance()

        // Check if worktree is already open in another window
        val existingProject = windowService.getOpenWorktreeProject(worktree.path)
        if (existingProject != null) {
            // Focus that window
            val frame = WindowManager.getInstance().getFrame(existingProject)
            frame?.toFront()
            return
        }

        // Open the worktree project
        val options = OpenProjectTask {
            forceOpenInNewFrame = true
            projectToClose = null
        }
        ProjectUtil.openOrImport(worktree.path, options)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !isCurrent
        if (isCurrent) {
            e.presentation.text = "${worktree.displayName} (current)"
        } else {
            e.presentation.text = worktree.displayName
        }

        // Add indicator if open in another window
        val windowService = WorktreeWindowService.getInstance()
        if (!isCurrent && windowService.isWorktreeOpen(worktree.path)) {
            e.presentation.text = "${worktree.displayName} (open)"
        }
    }
}
