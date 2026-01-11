package com.github.ccustine.worktree.actions

import com.github.ccustine.worktree.services.WorktreeService
import com.github.ccustine.worktree.services.WorktreeWindowService
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDirectory
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

/**
 * Context menu action to open a worktree directory in a new window.
 * Only visible when right-clicking on a directory that is a worktree.
 */
class OpenWorktreeFolderAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory ?: return

        val dirPath = Path.of(psiElement.virtualFile.path)
        val worktreeService = WorktreeService.getInstance(project)
        val windowService = WorktreeWindowService.getInstance()

        // Check if already open - focus that window instead
        val existingProject = windowService.getOpenWorktreeProject(dirPath)
        if (existingProject != null) {
            val frame = WindowManager.getInstance().getFrame(existingProject)
            frame?.toFront()
            return
        }

        // Open the worktree in a new window
        val options = OpenProjectTask {
            forceOpenInNewFrame = true
            projectToClose = null
        }
        ProjectUtil.openOrImport(dirPath, options)
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

    private fun hasGitRepository(project: com.intellij.openapi.project.Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
