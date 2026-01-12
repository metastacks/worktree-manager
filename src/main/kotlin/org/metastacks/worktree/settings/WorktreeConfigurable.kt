package org.metastacks.worktree.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class WorktreeConfigurable(private val project: Project) : BoundConfigurable("Worktree Manager") {

    private val settings = WorktreeSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group("Worktree Directory") {
            row("Default directory:") {
                textField()
                    .bindText(settings::defaultWorktreeDirectory)
                    .comment("Relative path from repository root (default: .worktrees)")
            }
        }
    }
}
