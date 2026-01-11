package com.github.ccustine.worktree.ui

import com.github.ccustine.worktree.services.WorktreeInfo
import com.github.ccustine.worktree.services.WorktreeService
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import javax.swing.Icon

/**
 * Decorates the project root node in the Project view to indicate when
 * the project is a git worktree, and decorates worktree directories
 * within the project with a branch icon.
 */
class WorktreeProjectNameDecorator : ProjectViewNodeDecorator {

    companion object {
        // Cache the green branch icon
        private val greenBranchIcon: Icon by lazy {
            IconUtil.colorize(AllIcons.Vcs.Branch, JBColor(0x59A869, 0x499C54))
        }
    }

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        if (node !is PsiDirectoryNode) return

        val project = node.project ?: return
        val psiDirectory = node.value ?: return

        // Check if project has Git
        if (!hasGitRepository(project)) return

        val directoryPath = psiDirectory.virtualFile.path
        val projectPath = project.basePath ?: return

        // Use non-blocking cached worktree data only
        val worktreeService = WorktreeService.getInstance(project)
        val worktrees = worktreeService.getCachedWorktrees() ?: return

        // Check if this directory is a worktree
        val dirPath = Path.of(directoryPath)
        val worktreeInfo = findWorktreeForPath(worktrees, dirPath)

        if (worktreeInfo != null && !worktreeInfo.isMain) {
            decorateWorktreeDirectory(data, worktreeInfo)
            return
        }

        // Check if this is the project root (for existing project root decoration)
        if (directoryPath == projectPath) {
            decorateProjectRoot(data, worktrees, projectPath)
        }
    }

    private fun decorateWorktreeDirectory(data: PresentationData, worktree: WorktreeInfo) {
        // Set the green branch icon for worktree directories
        data.setIcon(greenBranchIcon)

        // Add branch name as suffix
        val branchName = worktree.branch ?: "detached"
        data.addText(
            " [$branchName]",
            SimpleTextAttributes.GRAYED_ATTRIBUTES
        )
    }

    private fun decorateProjectRoot(data: PresentationData, worktrees: List<WorktreeInfo>, projectPath: String) {
        // Find current worktree
        val currentPath = Path.of(projectPath)
        val currentWorktree = worktrees.find { it.path == currentPath } ?: return

        // Only decorate if this is a non-main worktree
        if (currentWorktree.isMain) return

        // Add decoration
        val branchName = currentWorktree.branch ?: "detached"
        data.addText(
            " [worktree: $branchName]",
            SimpleTextAttributes.GRAYED_ATTRIBUTES
        )
    }

    private fun findWorktreeForPath(worktrees: List<WorktreeInfo>, path: Path): WorktreeInfo? {
        return worktrees.find { it.path == path }
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
