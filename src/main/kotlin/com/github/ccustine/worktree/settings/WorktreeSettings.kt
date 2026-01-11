package com.github.ccustine.worktree.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "WorktreeSettings",
    storages = [Storage("worktreeManager.xml")]
)
class WorktreeSettings : PersistentStateComponent<WorktreeSettings.State> {

    data class State(
        var defaultWorktreeDirectory: String = DEFAULT_WORKTREE_DIRECTORY
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var defaultWorktreeDirectory: String
        get() = myState.defaultWorktreeDirectory
        set(value) {
            myState.defaultWorktreeDirectory = value
        }

    companion object {
        const val DEFAULT_WORKTREE_DIRECTORY = ".worktrees"

        fun getInstance(project: Project): WorktreeSettings {
            return project.getService(WorktreeSettings::class.java)
        }
    }
}
