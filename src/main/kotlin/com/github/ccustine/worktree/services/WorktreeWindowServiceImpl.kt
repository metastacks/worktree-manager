package com.github.ccustine.worktree.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class WorktreeWindowServiceImpl : WorktreeWindowService, Disposable {

    private val openWorktrees = ConcurrentHashMap<Path, Project>()

    init {
        // Listen for project open/close events
        ProjectManager.getInstance().addProjectManagerListener(object : ProjectManagerListener {
            override fun projectOpened(project: Project) {
                val basePath = project.basePath?.let { Path.of(it) } ?: return
                registerOpenWorktree(basePath, project)
            }

            override fun projectClosed(project: Project) {
                val basePath = project.basePath?.let { Path.of(it) } ?: return
                unregisterWorktree(basePath)
            }
        })

        // Register already open projects
        ProjectManager.getInstance().openProjects.forEach { project ->
            val basePath = project.basePath?.let { Path.of(it) } ?: return@forEach
            registerOpenWorktree(basePath, project)
        }
    }

    override fun registerOpenWorktree(path: Path, project: Project) {
        openWorktrees[path] = project
    }

    override fun unregisterWorktree(path: Path) {
        openWorktrees.remove(path)
    }

    override fun isWorktreeOpen(path: Path): Boolean {
        // First check our cache
        val cachedProject = openWorktrees[path]
        if (cachedProject != null && !cachedProject.isDisposed) {
            return true
        }

        // Also check open projects directly in case cache is stale
        val normalizedPath = path.toAbsolutePath().normalize()
        return ProjectManager.getInstance().openProjects.any { project ->
            val projectPath = project.basePath?.let { Path.of(it).toAbsolutePath().normalize() }
            projectPath == normalizedPath && !project.isDisposed
        }
    }

    override fun getOpenWorktreeProject(path: Path): Project? {
        val normalizedPath = path.toAbsolutePath().normalize()
        // First check our cache with normalized path
        val project = openWorktrees.entries.find {
            it.key.toAbsolutePath().normalize() == normalizedPath
        }?.value
        if (project != null && !project.isDisposed) {
            return project
        }
        // Fall back to checking open projects directly
        return ProjectManager.getInstance().openProjects.find { proj ->
            val projectPath = proj.basePath?.let { Path.of(it).toAbsolutePath().normalize() }
            projectPath == normalizedPath && !proj.isDisposed
        }
    }

    override fun getOpenWorktrees(): Set<Path> {
        // Clean up disposed projects
        openWorktrees.entries.removeIf { it.value.isDisposed }
        return openWorktrees.keys.toSet()
    }

    override fun dispose() {
        openWorktrees.clear()
    }
}
