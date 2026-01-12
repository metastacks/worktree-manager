package org.metastacks.worktree.actions

import org.metastacks.worktree.services.WorktreeInfo
import org.metastacks.worktree.services.WorktreeService
import org.metastacks.worktree.services.WorktreeWindowService
import org.metastacks.worktree.ui.WorktreeListDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.repo.GitRepositoryManager

class RemoveWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val service = WorktreeService.getInstance(project)
        val windowService = WorktreeWindowService.getInstance()
        val worktrees = service.listWorktrees().filter { !it.isMain }

        if (worktrees.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No worktrees to remove.",
                "Remove Worktree"
            )
            return
        }

        val dialog = WorktreeListDialog(
            project = project,
            title = "Remove Worktree",
            worktrees = worktrees,
            windowService = windowService,
            actionButtonText = "Remove",
            showStatus = true,
            allowMultiSelect = true
        )

        if (dialog.showAndGet()) {
            val selectedWorktrees = dialog.selectedWorktrees
            if (selectedWorktrees.isEmpty()) return

            // Confirm if multiple selected
            if (selectedWorktrees.size > 1) {
                val confirm = Messages.showYesNoDialog(
                    project,
                    "Remove ${selectedWorktrees.size} worktrees?",
                    "Confirm Removal",
                    "Remove All",
                    "Cancel",
                    Messages.getQuestionIcon()
                )
                if (confirm != Messages.YES) return
            }

            for (selectedWorktree in selectedWorktrees) {
                // Safety checks
                if (!performSafetyChecks(project, selectedWorktree, windowService, service)) {
                    continue
                }

                val result = service.removeWorktree(selectedWorktree.path, force = false)
                result.onFailure { error ->
                    // If it failed due to uncommitted changes, offer force removal
                    val forceRemove = Messages.showYesNoDialog(
                        project,
                        "Failed to remove worktree '${selectedWorktree.displayName}': ${error.message}\n\nDo you want to force removal?",
                        "Remove Worktree",
                        "Force Remove",
                        "Cancel",
                        Messages.getWarningIcon()
                    )

                    if (forceRemove == Messages.YES) {
                        service.removeWorktree(selectedWorktree.path, force = true)
                            .onFailure { forceError ->
                                Messages.showErrorDialog(
                                    project,
                                    "Failed to force remove worktree '${selectedWorktree.displayName}': ${forceError.message}",
                                    "Remove Failed"
                                )
                            }
                    }
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

        // Check for uncommitted changes (fetch real-time status)
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

        // Check for unpushed commits (fetch real-time status)
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
        e.presentation.isEnabledAndVisible = project != null && hasGitRepository(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
