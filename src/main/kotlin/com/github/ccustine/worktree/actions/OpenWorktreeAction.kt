package com.github.ccustine.worktree.actions

import com.github.ccustine.worktree.services.WorktreeService
import com.github.ccustine.worktree.services.WorktreeWindowService
import com.github.ccustine.worktree.ui.WorktreeListDialog
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

class OpenWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val service = WorktreeService.getInstance(project)
        val windowService = WorktreeWindowService.getInstance()
        val worktrees = service.listWorktrees()

        if (worktrees.isEmpty()) {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "No worktrees found.",
                "Open Worktree"
            )
            return
        }

        val dialog = WorktreeListDialog(
            project = project,
            title = "Open Worktree",
            worktrees = worktrees,
            windowService = windowService,
            actionButtonText = "Open"
        )

        if (dialog.showAndGet()) {
            val selectedWorktree = dialog.selectedWorktree ?: return
            val path = selectedWorktree.path.toString()

            // Check if already open - focus that window instead
            val existingProject = windowService.getOpenWorktreeProject(selectedWorktree.path)
            if (existingProject != null) {
                val frame = WindowManager.getInstance().getFrame(existingProject)
                frame?.toFront()
                return
            }

            openProject(path)
        }
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

    private fun openProject(path: String) {
        val options = OpenProjectTask {
            forceOpenInNewFrame = true
            projectToClose = null
        }
        ProjectUtil.openOrImport(Path.of(path), options)
    }
}
