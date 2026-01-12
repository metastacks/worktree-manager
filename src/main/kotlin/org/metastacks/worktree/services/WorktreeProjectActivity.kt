package org.metastacks.worktree.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import java.nio.file.Path

/**
 * Registers projects with WorktreeWindowService when they open,
 * and unregisters them when they close.
 */
class WorktreeProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val basePath = project.basePath?.let { Path.of(it) } ?: return
        val windowService = WorktreeWindowService.getInstance()

        // Register this project as open
        windowService.registerOpenWorktree(basePath, project)

        // Register cleanup when project closes
        Disposer.register(project, Disposable {
            windowService.unregisterWorktree(basePath)
        })
    }
}
