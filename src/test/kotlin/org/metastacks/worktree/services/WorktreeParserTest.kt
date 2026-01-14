package org.metastacks.worktree.services

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path

class WorktreeParserTest {

    @Test
    fun `parses single main worktree`() {
        val output = listOf(
            "worktree /home/user/project",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(1, worktrees.size)
        with(worktrees[0]) {
            assertEquals(Path.of("/home/user/project"), path)
            assertEquals("main", branch)
            assertEquals("abc123def456789012345678901234567890abcd", commitHash)
            assertTrue(isMain)
        }
    }

    @Test
    fun `parses multiple worktrees with main first`() {
        val output = listOf(
            "worktree /home/user/project",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            "",
            "worktree /home/user/project/.worktrees/feature",
            "HEAD def456789012345678901234567890abcdef12",
            "branch refs/heads/feature-branch",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(2, worktrees.size)

        with(worktrees[0]) {
            assertEquals(Path.of("/home/user/project"), path)
            assertEquals("main", branch)
            assertTrue(isMain)
        }

        with(worktrees[1]) {
            assertEquals(Path.of("/home/user/project/.worktrees/feature"), path)
            assertEquals("feature-branch", branch)
            assertFalse(isMain)
        }
    }

    @Test
    fun `parses worktree with detached HEAD (no branch)`() {
        val output = listOf(
            "worktree /home/user/project",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            "",
            "worktree /home/user/project/.worktrees/detached",
            "HEAD def456789012345678901234567890abcdef12",
            "detached",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(2, worktrees.size)

        with(worktrees[1]) {
            assertEquals(Path.of("/home/user/project/.worktrees/detached"), path)
            assertNull(branch)
            assertEquals("def456789012345678901234567890abcdef12", commitHash)
            assertFalse(isMain)
        }
    }

    @Test
    fun `skips bare repository entries without HEAD`() {
        // Bare repository entries don't have a HEAD commit, so they are skipped
        // by the parser (which requires a commit hash)
        val output = listOf(
            "worktree /home/user/project.git",
            "bare",
            "",
            "worktree /home/user/worktrees/main",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        // Only the worktree with HEAD is parsed; bare entry is skipped
        assertEquals(1, worktrees.size)

        with(worktrees[0]) {
            assertEquals(Path.of("/home/user/worktrees/main"), path)
            assertEquals("main", branch)
            // Since it's the first successfully parsed worktree, it's marked as main
            assertTrue(isMain)
        }
    }

    @Test
    fun `handles empty output`() {
        val output = emptyList<String>()

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertTrue(worktrees.isEmpty())
    }

    @Test
    fun `handles output with only empty lines`() {
        val output = listOf("", "", "")

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertTrue(worktrees.isEmpty())
    }

    @Test
    fun `parses three worktrees correctly`() {
        val output = listOf(
            "worktree /repo",
            "HEAD 1111111111111111111111111111111111111111",
            "branch refs/heads/main",
            "",
            "worktree /repo/.worktrees/feature-a",
            "HEAD 2222222222222222222222222222222222222222",
            "branch refs/heads/feature-a",
            "",
            "worktree /repo/.worktrees/feature-b",
            "HEAD 3333333333333333333333333333333333333333",
            "branch refs/heads/feature-b",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(3, worktrees.size)
        assertEquals("main", worktrees[0].branch)
        assertEquals("feature-a", worktrees[1].branch)
        assertEquals("feature-b", worktrees[2].branch)

        assertTrue(worktrees[0].isMain)
        assertFalse(worktrees[1].isMain)
        assertFalse(worktrees[2].isMain)
    }

    @Test
    fun `parses worktree without trailing empty line`() {
        val output = listOf(
            "worktree /repo",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main"
            // Note: no trailing empty line
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(1, worktrees.size)
        assertEquals(Path.of("/repo"), worktrees[0].path)
        assertEquals("main", worktrees[0].branch)
        assertTrue(worktrees[0].isMain)
    }

    @Test
    fun `parses paths with spaces`() {
        val output = listOf(
            "worktree /home/user/my project",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            "",
            "worktree /home/user/my project/.worktrees/feature branch",
            "HEAD def456789012345678901234567890abcdef12",
            "branch refs/heads/feature-branch",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(2, worktrees.size)
        assertEquals(Path.of("/home/user/my project"), worktrees[0].path)
        assertEquals(Path.of("/home/user/my project/.worktrees/feature branch"), worktrees[1].path)
    }

    @Test
    fun `parses nested branch names with slashes`() {
        val output = listOf(
            "worktree /repo",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/feature/nested/branch",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(1, worktrees.size)
        // The parser uses substringAfter("refs/heads/") so nested branches work
        assertEquals("feature/nested/branch", worktrees[0].branch)
    }

    @Test
    fun `all parsed worktrees have isDirty and hasUnpushedCommits as false`() {
        val output = listOf(
            "worktree /repo",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            "",
            "worktree /repo/.worktrees/feature",
            "HEAD def456789012345678901234567890abcdef12",
            "branch refs/heads/feature",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        worktrees.forEach { worktree ->
            assertFalse("isDirty should default to false", worktree.isDirty)
            assertFalse("hasUnpushedCommits should default to false", worktree.hasUnpushedCommits)
        }
    }

    @Test
    fun `parses worktree with locked state`() {
        // Git adds a "locked" line for locked worktrees
        val output = listOf(
            "worktree /repo",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            "",
            "worktree /repo/.worktrees/locked-feature",
            "HEAD def456789012345678901234567890abcdef12",
            "branch refs/heads/locked-feature",
            "locked",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        // Parser should still work, just ignores the locked line
        assertEquals(2, worktrees.size)
        assertEquals("locked-feature", worktrees[1].branch)
    }

    @Test
    fun `parses worktree with prunable state`() {
        // Git adds a "prunable" line for prunable worktrees
        val output = listOf(
            "worktree /repo",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            "",
            "worktree /repo/.worktrees/prunable-feature",
            "HEAD def456789012345678901234567890abcdef12",
            "branch refs/heads/prunable-feature",
            "prunable",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        // Parser should still work, just ignores the prunable line
        assertEquals(2, worktrees.size)
        assertEquals("prunable-feature", worktrees[1].branch)
    }

    @Test
    fun `first worktree is always marked as main when no bare indicator`() {
        val output = listOf(
            "worktree /first",
            "HEAD 1111111111111111111111111111111111111111",
            "branch refs/heads/develop",
            "",
            "worktree /second",
            "HEAD 2222222222222222222222222222222222222222",
            "branch refs/heads/main",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(2, worktrees.size)
        assertTrue("First worktree should be marked as main", worktrees[0].isMain)
        assertFalse("Second worktree should not be main", worktrees[1].isMain)
    }

    @Test
    fun `handles Windows-style paths`() {
        val output = listOf(
            "worktree C:/Users/dev/project",
            "HEAD abc123def456789012345678901234567890abcd",
            "branch refs/heads/main",
            ""
        )

        val worktrees = WorktreeServiceImpl.parseWorktreeList(output)

        assertEquals(1, worktrees.size)
        assertEquals(Path.of("C:/Users/dev/project"), worktrees[0].path)
    }
}
