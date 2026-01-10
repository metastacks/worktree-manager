package com.github.ccustine.worktree.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.config.GitExecutableManager
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class WorktreeServiceImpl(private val project: Project) : WorktreeService {

    private val logger = Logger.getInstance(WorktreeServiceImpl::class.java)

    private val repositoryManager: GitRepositoryManager
        get() = GitRepositoryManager.getInstance(project)

    private fun getGitExecutable(): String {
        return GitExecutableManager.getInstance().getExecutable(project).exePath
    }

    private fun runGitCommand(workDir: Path, vararg args: String): Pair<Boolean, List<String>> {
        val commandLine = GeneralCommandLine()
            .withExePath(getGitExecutable())
            .withWorkDirectory(workDir.toFile())
            .withParameters(*args)

        return try {
            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(30000)  // 30 second timeout

            if (result.exitCode == 0) {
                Pair(true, result.stdoutLines)
            } else {
                logger.warn("Git command failed: ${result.stderr}")
                Pair(false, result.stderrLines)
            }
        } catch (e: Exception) {
            logger.error("Failed to execute git command", e)
            Pair(false, listOf(e.message ?: "Unknown error"))
        }
    }

    override fun listWorktrees(): List<WorktreeInfo> {
        val repository = repositoryManager.repositories.firstOrNull() ?: return emptyList()
        val root = repository.root.toNioPath()

        val (success, output) = runGitCommand(root, "worktree", "list", "--porcelain")
        if (!success) {
            return emptyList()
        }

        return parseWorktreeList(output)
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
                                    isDirty = hasUncommittedChanges(path),
                                    hasUnpushedCommits = hasUnpushedCommits(path)
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
                        isDirty = hasUncommittedChanges(path),
                        hasUnpushedCommits = hasUnpushedCommits(path)
                    )
                )
            }
        }

        return worktrees
    }

    override fun createWorktree(branch: String, path: Path, createBranch: Boolean): Result<WorktreeInfo> {
        val repository = repositoryManager.repositories.firstOrNull()
            ?: return Result.failure(IllegalStateException("No git repository found"))

        val root = repository.root.toNioPath()

        val args = mutableListOf("worktree", "add")
        if (createBranch) {
            args.addAll(listOf("-b", branch))
        }
        args.add(path.toString())
        if (!createBranch) {
            args.add(branch)
        }

        val (success, output) = runGitCommand(root, *args.toTypedArray())
        if (!success) {
            val error = output.joinToString("\n")
            return Result.failure(RuntimeException(error))
        }

        return getWorktreeInfo(path)?.let { Result.success(it) }
            ?: Result.failure(RuntimeException("Worktree created but could not retrieve info"))
    }

    override fun removeWorktree(path: Path, force: Boolean): Result<Unit> {
        val repository = repositoryManager.repositories.firstOrNull()
            ?: return Result.failure(IllegalStateException("No git repository found"))

        val root = repository.root.toNioPath()

        val args = mutableListOf("worktree", "remove")
        if (force) {
            args.add("--force")
        }
        args.add(path.toString())

        val (success, output) = runGitCommand(root, *args.toTypedArray())
        if (!success) {
            val error = output.joinToString("\n")
            return Result.failure(RuntimeException(error))
        }

        return Result.success(Unit)
    }

    override fun getWorktreeInfo(path: Path): WorktreeInfo? {
        return listWorktrees().find { it.path == path }
    }

    override fun isWorktree(): Boolean {
        val projectPath = project.basePath?.let { Path.of(it) } ?: return false
        val worktrees = listWorktrees()
        val currentWorktree = worktrees.find { it.path == projectPath }
        return currentWorktree != null && !currentWorktree.isMain
    }

    override fun getMainRepositoryPath(): Path? {
        return listWorktrees().find { it.isMain }?.path
    }

    override fun getDefaultWorktreePath(branchName: String): Path? {
        val mainPath = getMainRepositoryPath() ?: return null
        val sanitizedBranch = branchName
            .replace("/", "-")
            .replace("\\", "-")
            .replace(" ", "-")
        return mainPath.resolve(".worktrees").resolve(sanitizedBranch)
    }

    override fun hasUncommittedChanges(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false

        val (success, output) = runGitCommand(path, "status", "--porcelain")
        return success && output.isNotEmpty()
    }

    override fun hasUnpushedCommits(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false

        val (success, output) = runGitCommand(path, "log", "@{u}..HEAD", "--oneline")
        // If command fails (e.g., no upstream), we can't determine unpushed status
        return success && output.isNotEmpty()
    }
}
