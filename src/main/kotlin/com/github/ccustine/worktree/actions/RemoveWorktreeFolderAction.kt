package com.github.ccustine.worktree.actions

import com.github.ccustine.worktree.services.WorktreeInfo
import com.github.ccustine.worktree.services.WorktreeService
import com.github.ccustine.worktree.services.WorktreeWindowService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

/**
 * Context menu action to remove a worktree directory.
 * Only visible when right-clicking on a directory that is a worktree.
 */
class RemoveWorktreeFolderAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory ?: return

        val dirPath = Path.of(psiElement.virtualFile.path)
        val worktreeService = WorktreeService.getInstance(project)
        val windowService = WorktreeWindowService.getInstance()

        val worktrees = worktreeService.listWorktrees()
        val worktreeInfo = worktrees.find { it.path == dirPath } ?: return

        // Safety checks
        if (!performSafetyChecks(project, worktreeInfo, windowService, worktreeService)) {
            return
        }

        // Confirm removal
        val confirm = Messages.showYesNoDialog(
            project,
            "Remove worktree '${worktreeInfo.displayName}'?",
            "Remove Worktree",
            "Remove",
            "Cancel",
            Messages.getQuestionIcon()
        )

        if (confirm != Messages.YES) return

        val result = worktreeService.removeWorktree(dirPath, force = false)
        result.onFailure { error ->
            // If it failed, offer force removal
            val forceRemove = Messages.showYesNoDialog(
                project,
                "Failed to remove worktree: ${error.message}\n\nDo you want to force removal?",
                "Remove Worktree",
                "Force Remove",
                "Cancel",
                Messages.getWarningIcon()
            )

            if (forceRemove == Messages.YES) {
                worktreeService.removeWorktree(dirPath, force = true)
                    .onFailure { forceError ->
                        Messages.showErrorDialog(
                            project,
                            "Failed to force remove worktree: ${forceError.message}",
                            "Remove Failed"
                        )
                    }
            }
        }
    }

    private fun performSafetyChecks(
        project: Project,
        worktree: WorktreeInfo,
        windowService: WorktreeWindowService,
        worktreeService: WorktreeService
    ): Boolean {
        // Check if worktree is open in another window
        if (windowService.isWorktreeOpen(worktree.path)) {
            Messages.showWarningDialog(
                project,
                "This worktree is currently open in another window.\nPlease close it before removing.",
                "Cannot Remove Worktree"
            )
            return false
        }

        // Check for uncommitted changes
        val hasUncommittedChanges = worktreeService.hasUncommittedChanges(worktree.path)
        if (hasUncommittedChanges) {
            val proceed = Messages.showYesNoDialog(
                project,
                "This worktree has uncommitted changes.\nAre you sure you want to remove it?",
                "Uncommitted Changes",
                "Remove Anyway",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (proceed != Messages.YES) {
                return false
            }
        }

        // Check for unpushed commits
        val hasUnpushedCommits = worktreeService.hasUnpushedCommits(worktree.path)
        if (hasUnpushedCommits) {
            val proceed = Messages.showYesNoDialog(
                project,
                "This worktree has unpushed commits.\nAre you sure you want to remove it?",
                "Unpushed Commits",
                "Remove Anyway",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (proceed != Messages.YES) {
                return false
            }
        }

        return true
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)

        if (project == null || psiElement !is PsiDirectory) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Check if project has Git
        if (!hasGitRepository(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Check if this directory is a worktree
        val worktreeService = WorktreeService.getInstance(project)
        val worktrees = worktreeService.getCachedWorktrees()

        if (worktrees == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val dirPath = Path.of(psiElement.virtualFile.path)
        val worktreeInfo = worktrees.find { it.path == dirPath }

        // Only show for non-main worktrees that are not the current project
        val projectPath = project.basePath?.let { Path.of(it) }
        val isCurrentProject = dirPath == projectPath

        e.presentation.isEnabledAndVisible = worktreeInfo != null && !worktreeInfo.isMain && !isCurrentProject
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
