package com.github.ccustine.worktree.ui

import com.github.ccustine.worktree.services.WorktreeInfo
import com.github.ccustine.worktree.services.WorktreeWindowService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class WorktreeListDialog(
    project: Project,
    title: String,
    private val worktrees: List<WorktreeInfo>,
    private val windowService: WorktreeWindowService,
    actionButtonText: String,
    private val showStatus: Boolean = false,
    private val allowMultiSelect: Boolean = false
) : DialogWrapper(project) {

    private val tableModel = WorktreeTableModel(worktrees, windowService, showStatus)
    private val table = JBTable(tableModel)

    var selectedWorktree: WorktreeInfo? = null
        private set

    var selectedWorktrees: List<WorktreeInfo> = emptyList()
        private set

    init {
        this.title = title
        setOKButtonText(actionButtonText)
        init()
        setupTable()
    }

    private fun setupTable() {
        table.selectionModel.selectionMode = if (allowMultiSelect) {
            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        } else {
            ListSelectionModel.SINGLE_SELECTION
        }
        table.setShowGrid(false)
        table.rowHeight = 24

        // Set column widths
        table.columnModel.getColumn(0).preferredWidth = 150  // Branch
        table.columnModel.getColumn(1).preferredWidth = 300  // Path
        table.columnModel.getColumn(2).preferredWidth = 80   // Status

        // Custom renderer for status column
        table.columnModel.getColumn(2).cellRenderer = object : ColoredTableCellRenderer() {
            override fun customizeCellRenderer(
                table: JTable,
                value: Any?,
                selected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ) {
                val worktree = worktrees.getOrNull(row) ?: return
                val status = value as? String ?: ""

                when {
                    worktree.isMain -> {
                        append("MAIN", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
                    }
                    windowService.isWorktreeOpen(worktree.path) -> {
                        append("OPEN", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    }
                    worktree.isDirty -> {
                        append("DIRTY", SimpleTextAttributes.ERROR_ATTRIBUTES)
                    }
                    worktree.hasUnpushedCommits -> {
                        append("UNPUSHED", SimpleTextAttributes(
                            SimpleTextAttributes.STYLE_PLAIN,
                            java.awt.Color.ORANGE
                        ))
                    }
                    else -> append(status)
                }
            }
        }

        // Select first non-main worktree by default
        val defaultSelection = worktrees.indexOfFirst { !it.isMain }
        if (defaultSelection >= 0) {
            table.selectionModel.setSelectionInterval(defaultSelection, defaultSelection)
        }
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JScrollPane(table)
        scrollPane.preferredSize = Dimension(550, 200)

        val panel = JPanel(java.awt.BorderLayout())
        panel.add(scrollPane, java.awt.BorderLayout.CENTER)
        return panel
    }

    override fun doOKAction() {
        val selectedRows = table.selectedRows
        if (selectedRows.isNotEmpty()) {
            selectedWorktrees = selectedRows.map { worktrees[it] }
            selectedWorktree = selectedWorktrees.firstOrNull()
        }
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent = table
}

private class WorktreeTableModel(
    private val worktrees: List<WorktreeInfo>,
    private val windowService: WorktreeWindowService,
    private val showStatus: Boolean
) : AbstractTableModel() {

    private val columns = listOf("Branch", "Path", "Status")

    override fun getRowCount(): Int = worktrees.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val worktree = worktrees[rowIndex]
        return when (columnIndex) {
            0 -> worktree.displayName
            1 -> worktree.path.toString()
            2 -> getStatus(worktree)
            else -> ""
        }
    }

    private fun getStatus(worktree: WorktreeInfo): String {
        return when {
            worktree.isMain -> "MAIN"
            windowService.isWorktreeOpen(worktree.path) -> "OPEN"
            worktree.isDirty && showStatus -> "DIRTY"
            worktree.hasUnpushedCommits && showStatus -> "UNPUSHED"
            else -> ""
        }
    }
}
