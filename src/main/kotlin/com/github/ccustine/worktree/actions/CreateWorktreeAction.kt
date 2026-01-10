package com.github.ccustine.worktree.actions

import com.github.ccustine.worktree.services.WorktreeService
import com.github.ccustine.worktree.ui.CreateWorktreeDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import git4idea.repo.GitRepositoryManager

class CreateWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dialog = CreateWorktreeDialog(project)
        if (dialog.showAndGet()) {
            val branchName = dialog.branchName
            val worktreePath = dialog.worktreePath
            val createNewBranch = dialog.createNewBranch
            val openAfterCreation = dialog.openAfterCreation

            val service = WorktreeService.getInstance(project)
            val result = service.createWorktree(branchName, worktreePath, createNewBranch)

            result.onSuccess { worktreeInfo ->
                if (openAfterCreation) {
                    openProject(worktreeInfo.path.toString())
                }
            }.onFailure { error ->
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Failed to create worktree: ${error.message}",
                    "Worktree Creation Failed"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && hasGitRepository(project)
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }

    private fun openProject(path: String) {
        ProjectManager.getInstance().loadAndOpenProject(path)
    }
}
