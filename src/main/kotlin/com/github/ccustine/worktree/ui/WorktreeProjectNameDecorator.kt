package com.github.ccustine.worktree.ui

import com.github.ccustine.worktree.services.WorktreeService
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

/**
 * Decorates the project root node in the Project view to indicate when
 * the project is a git worktree.
 */
class WorktreeProjectNameDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        if (node !is PsiDirectoryNode) return

        val project = node.project ?: return
        val psiDirectory = node.value ?: return

        // Only decorate the project root
        val projectPath = project.basePath ?: return
        val directoryPath = psiDirectory.virtualFile.path

        if (directoryPath != projectPath) return

        // Check if project has Git
        if (!hasGitRepository(project)) return

        // Check if this is a worktree
        val worktreeService = WorktreeService.getInstance(project)
        if (!worktreeService.isWorktree()) return

        // Get the current worktree info
        val currentPath = Path.of(projectPath)
        val worktrees = worktreeService.listWorktrees()
        val currentWorktree = worktrees.find { it.path == currentPath } ?: return

        // Add decoration
        val branchName = currentWorktree.branch ?: "detached"
        data.addText(
            " [worktree: $branchName]",
            SimpleTextAttributes.GRAYED_ATTRIBUTES
        )
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
