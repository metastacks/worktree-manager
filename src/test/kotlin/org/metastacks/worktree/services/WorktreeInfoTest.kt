package org.metastacks.worktree.services

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path

class WorktreeInfoTest {

    @Test
    fun `displayName returns branch name when branch is present`() {
        val worktree = WorktreeInfo(
            path = Path.of("/repo/.worktrees/feature-branch"),
            branch = "feature-branch",
            commitHash = "abc123def456789",
            isMain = false
        )

        assertEquals("feature-branch", worktree.displayName)
    }

    @Test
    fun `displayName returns path filename when branch is null`() {
        val worktree = WorktreeInfo(
            path = Path.of("/repo/.worktrees/detached-head"),
            branch = null,
            commitHash = "abc123def456789",
            isMain = false
        )

        assertEquals("detached-head", worktree.displayName)
    }

    @Test
    fun `shortHash returns first 7 characters of commit hash`() {
        val worktree = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = "abc123def456789abcdef",
            isMain = true
        )

        assertEquals("abc123d", worktree.shortHash)
    }

    @Test
    fun `shortHash handles short commit hashes gracefully`() {
        val worktree = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = "abc",
            isMain = true
        )

        assertEquals("abc", worktree.shortHash)
    }

    @Test
    fun `shortHash returns exactly 7 chars for standard 40-char hash`() {
        val fullHash = "1234567890abcdef1234567890abcdef12345678"
        val worktree = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = fullHash,
            isMain = true
        )

        assertEquals(7, worktree.shortHash.length)
        assertEquals("1234567", worktree.shortHash)
    }

    @Test
    fun `data class equality works correctly`() {
        val worktree1 = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = "abc123",
            isMain = true,
            isDirty = false,
            hasUnpushedCommits = false
        )

        val worktree2 = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = "abc123",
            isMain = true,
            isDirty = false,
            hasUnpushedCommits = false
        )

        assertEquals(worktree1, worktree2)
        assertEquals(worktree1.hashCode(), worktree2.hashCode())
    }

    @Test
    fun `data class inequality when paths differ`() {
        val worktree1 = WorktreeInfo(
            path = Path.of("/repo1"),
            branch = "main",
            commitHash = "abc123",
            isMain = true
        )

        val worktree2 = WorktreeInfo(
            path = Path.of("/repo2"),
            branch = "main",
            commitHash = "abc123",
            isMain = true
        )

        assertNotEquals(worktree1, worktree2)
    }

    @Test
    fun `isDirty and hasUnpushedCommits default to false`() {
        val worktree = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = "abc123",
            isMain = true
        )

        assertFalse(worktree.isDirty)
        assertFalse(worktree.hasUnpushedCommits)
    }

    @Test
    fun `copy with modified isDirty preserves other fields`() {
        val original = WorktreeInfo(
            path = Path.of("/repo"),
            branch = "main",
            commitHash = "abc123",
            isMain = true,
            isDirty = false,
            hasUnpushedCommits = false
        )

        val dirty = original.copy(isDirty = true)

        assertEquals(original.path, dirty.path)
        assertEquals(original.branch, dirty.branch)
        assertEquals(original.commitHash, dirty.commitHash)
        assertEquals(original.isMain, dirty.isMain)
        assertTrue(dirty.isDirty)
        assertFalse(dirty.hasUnpushedCommits)
    }
}
