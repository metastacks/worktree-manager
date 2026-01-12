package com.github.ccustine.worktree.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class WorktreeServiceImpl(private val project: Project) : WorktreeService, Disposable {

    private val logger = Logger.getInstance(WorktreeServiceImpl::class.java)

    private val repositoryManager: GitRepositoryManager
        get() = GitRepositoryManager.getInstance(project)

    private val git: Git
        get() = Git.getInstance()

    // Cache for worktree list to avoid blocking UI during frequent update() calls
    private val worktreeCache = AtomicReference<CachedWorktrees?>(null)

    private data class CachedWorktrees(
        val worktrees: List<WorktreeInfo>,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }

    companion object {
        private const val CACHE_TTL_MS = 5000L // 5 seconds
    }

    /**
     * Runs a computation on a pooled thread and returns the result.
     * If already on a background thread, runs directly.
     */
    private fun <T> runInBackground(computation: () -> T): T {
        return if (ApplicationManager.getApplication().isDispatchThread ||
                   ApplicationManager.getApplication().isReadAccessAllowed) {
            // On EDT or in ReadAction - run on pooled thread and wait
            val future: Future<T> = ApplicationManager.getApplication().executeOnPooledThread(Callable { computation() })
            future.get()
        } else {
            // Already on background thread with no read lock
            computation()
        }
    }

    private fun runGitCommand(workDir: VirtualFile, command: GitCommand, vararg args: String): Pair<Boolean, List<String>> {
        return runInBackground {
            val handler = GitLineHandler(project, workDir, command)
            handler.addParameters(*args)

            try {
                val result = git.runCommand(handler)
                if (result.success()) {
                    Pair(true, result.output)
                } else {
                    logger.warn("Git command failed: ${result.errorOutputAsJoinedString}")
                    Pair(false, result.errorOutput)
                }
            } catch (e: Exception) {
                logger.error("Failed to execute git command", e)
                Pair(false, listOf(e.message ?: "Unknown error"))
            }
        }
    }

    private fun pathToVirtualFile(path: Path): VirtualFile? {
        return LocalFileSystem.getInstance().findFileByNioFile(path)
    }

    override fun listWorktrees(): List<WorktreeInfo> {
        // Check cache first
        val cached = worktreeCache.get()
        if (cached != null && !cached.isExpired()) {
            return cached.worktrees
        }

        // If on EDT or in ReadAction, never block - return stale/empty cache and refresh async
        if (ApplicationManager.getApplication().isDispatchThread ||
            ApplicationManager.getApplication().isReadAccessAllowed) {
            refreshWorktreeCacheAsync()
            return cached?.worktrees ?: emptyList()
        }

        return refreshWorktreeCache()
    }

    override fun getCachedWorktrees(): List<WorktreeInfo>? {
        val cached = worktreeCache.get()
        if (cached == null) {
            // No cache yet - trigger background refresh but don't block
            refreshWorktreeCacheAsync()
            return null
        }
        if (cached.isExpired()) {
            // Cache expired - trigger background refresh
            refreshWorktreeCacheAsync()
        }
        return cached.worktrees
    }

    private fun refreshWorktreeCache(): List<WorktreeInfo> {
        val repository = repositoryManager.repositories.firstOrNull() ?: return emptyList()
        val root = repository.root

        val (success, output) = runGitCommand(root, GitCommand.WORKTREE, "list", "--porcelain")
        if (!success) {
            return emptyList()
        }

        val worktrees = parseWorktreeList(output)
        worktreeCache.set(CachedWorktrees(worktrees, System.currentTimeMillis()))
        return worktrees
    }

    private fun refreshWorktreeCacheAsync() {
        ApplicationManager.getApplication().executeOnPooledThread {
            refreshWorktreeCache()
        }
    }

    /**
     * Force refresh of the worktree cache. Call after create/remove operations.
     */
    fun invalidateCache() {
        worktreeCache.set(null)
    }

    private fun parseWorktreeList(output: List<String>): List<WorktreeInfo> {
        val worktrees = mutableListOf<WorktreeInfo>()
        var currentPath: Path? = null
        var currentBranch: String? = null
        var currentCommit: String? = null
        var isMain = false

        for (line in output) {
            when {
                line.startsWith("worktree ") -> {
                    // Save previous worktree if exists
                    currentPath?.let { path ->
                        currentCommit?.let { commit ->
                            worktrees.add(
                                WorktreeInfo(
                                    path = path,
                                    branch = currentBranch,
                                    commitHash = commit,
                                    isMain = isMain,
                                    // Don't block on status checks - default to false
                                    // Status will be populated asynchronously if needed
                                    isDirty = false,
                                    hasUnpushedCommits = false
                                )
                            )
                        }
                    }
                    // Start new worktree
                    currentPath = Path.of(line.substringAfter("worktree "))
                    currentBranch = null
                    currentCommit = null
                    isMain = false
                }
                line.startsWith("HEAD ") -> {
                    currentCommit = line.substringAfter("HEAD ")
                }
                line.startsWith("branch ") -> {
                    currentBranch = line.substringAfter("branch refs/heads/")
                }
                line == "bare" -> {
                    isMain = true
                }
                line.isEmpty() -> {
                    // End of worktree entry - first one is usually the main worktree
                    if (worktrees.isEmpty()) {
                        isMain = true
                    }
                }
            }
        }

        // Don't forget the last worktree
        currentPath?.let { path ->
            currentCommit?.let { commit ->
                worktrees.add(
                    WorktreeInfo(
                        path = path,
                        branch = currentBranch,
                        commitHash = commit,
                        isMain = isMain || worktrees.isEmpty(),
                        // Don't block on status checks - default to false
                        isDirty = false,
                        hasUnpushedCommits = false
                    )
                )
            }
        }

        return worktrees
    }

    // Internal check methods that don't trigger cache
    private fun checkUncommittedChanges(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false
        val vFile = pathToVirtualFile(path) ?: return false

        return runInBackground {
            val handler = GitLineHandler(project, vFile, GitCommand.STATUS)
            handler.addParameters("--porcelain")

            try {
                val result = git.runCommand(handler)
                result.success() && result.output.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun checkUnpushedCommits(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false
        val vFile = pathToVirtualFile(path) ?: return false

        return runInBackground {
            val handler = GitLineHandler(project, vFile, GitCommand.LOG)
            handler.addParameters("@{u}..HEAD", "--oneline")

            try {
                val result = git.runCommand(handler)
                result.success() && result.output.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun createWorktree(branch: String, path: Path, createBranch: Boolean): Result<WorktreeInfo> {
        val repository = repositoryManager.repositories.firstOrNull()
            ?: return Result.failure(IllegalStateException("No git repository found"))

        val root = repository.root

        // Create parent directory structure if it doesn't exist
        val parentDir = path.parent
        if (parentDir != null && !parentDir.exists()) {
            try {
                parentDir.createDirectories()
            } catch (e: Exception) {
                return Result.failure(RuntimeException("Failed to create directory structure: ${e.message}"))
            }
        }

        val args = mutableListOf("add")
        if (createBranch) {
            args.addAll(listOf("-b", branch))
        }
        args.add(path.toString())
        if (!createBranch) {
            args.add(branch)
        }

        val (success, output) = runGitCommand(root, GitCommand.WORKTREE, *args.toTypedArray())
        if (!success) {
            val error = output.joinToString("\n")
            return Result.failure(RuntimeException(error))
        }

        // Refresh VFS synchronously to show the new directory immediately
        if (parentDir != null) {
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, parentDir.toFile())
        }

        // Force synchronous cache refresh after modification to get the new worktree
        val worktrees = refreshWorktreeCache()

        return worktrees.find { it.path == path }?.let { Result.success(it) }
            ?: Result.failure(RuntimeException("Worktree created but could not retrieve info"))
    }

    override fun removeWorktree(path: Path, force: Boolean): Result<Unit> {
        val repository = repositoryManager.repositories.firstOrNull()
            ?: return Result.failure(IllegalStateException("No git repository found"))

        val root = repository.root

        val args = mutableListOf("remove")
        if (force) {
            args.add("--force")
        }
        args.add(path.toString())

        val (success, output) = runGitCommand(root, GitCommand.WORKTREE, *args.toTypedArray())
        if (!success) {
            val error = output.joinToString("\n")
            return Result.failure(RuntimeException(error))
        }

        // Invalidate cache after modification
        invalidateCache()

        // Clean up VCS directory mapping for the removed worktree
        removeVcsMapping(path)

        // Refresh VFS synchronously to reflect the removed directory immediately
        val parentDir = path.parent
        if (parentDir != null) {
            // Mark dirty and force refresh to ensure filesystem is rescanned
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, parentDir.toFile())
        }

        return Result.success(Unit)
    }

    private fun removeVcsMapping(worktreePath: Path) {
        try {
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            val currentMappings = vcsManager.directoryMappings
            val pathString = worktreePath.toString()

            // Filter out any mappings that point to the removed worktree
            val updatedMappings = currentMappings.filter { mapping ->
                !mapping.directory.equals(pathString, ignoreCase = true) &&
                !mapping.directory.startsWith("$pathString/") &&
                !mapping.directory.startsWith("$pathString\\")
            }

            if (updatedMappings.size != currentMappings.size) {
                vcsManager.directoryMappings = updatedMappings
                logger.info("Removed VCS mapping for worktree: $pathString")
            }
        } catch (e: Exception) {
            logger.warn("Failed to remove VCS mapping for worktree: ${e.message}")
        }
    }

    override fun getWorktreeInfo(path: Path): WorktreeInfo? {
        val normalizedPath = path.toAbsolutePath().normalize()
        return listWorktrees().find { it.path.toAbsolutePath().normalize() == normalizedPath }
    }

    override fun isWorktree(): Boolean {
        val projectPath = project.basePath?.let { Path.of(it).toAbsolutePath().normalize() } ?: return false
        val worktrees = listWorktrees()
        val currentWorktree = worktrees.find { it.path.toAbsolutePath().normalize() == projectPath }
        return currentWorktree != null && !currentWorktree.isMain
    }

    override fun getMainRepositoryPath(): Path? {
        return listWorktrees().find { it.isMain }?.path
    }

    override fun getDefaultWorktreePath(branchName: String): Path? {
        val mainPath = getMainRepositoryPath() ?: return null
        val settings = com.github.ccustine.worktree.settings.WorktreeSettings.getInstance(project)
        val configuredDir = settings.defaultWorktreeDirectory
        // Use .worktrees as fallback if setting is empty or blank
        val worktreeDir = if (configuredDir.isNullOrBlank()) {
            com.github.ccustine.worktree.settings.WorktreeSettings.DEFAULT_WORKTREE_DIRECTORY
        } else {
            configuredDir
        }
        val sanitizedBranch = branchName
            .replace("/", "-")
            .replace("\\", "-")
            .replace(" ", "-")
        return mainPath.resolve(worktreeDir).resolve(sanitizedBranch)
    }

    override fun hasUncommittedChanges(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false

        val vFile = pathToVirtualFile(path) ?: return false
        val (success, output) = runGitCommand(vFile, GitCommand.STATUS, "--porcelain")
        return success && output.isNotEmpty()
    }

    override fun hasUnpushedCommits(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false

        val vFile = pathToVirtualFile(path) ?: return false
        val (success, output) = runGitCommand(vFile, GitCommand.LOG, "@{u}..HEAD", "--oneline")
        // If command fails (e.g., no upstream), we can't determine unpushed status
        return success && output.isNotEmpty()
    }

    override fun dispose() {
        worktreeCache.set(null)
    }
}
