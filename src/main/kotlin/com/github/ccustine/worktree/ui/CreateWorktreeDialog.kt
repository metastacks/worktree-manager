package com.github.ccustine.worktree.ui

import com.github.ccustine.worktree.services.WorktreeService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepositoryManager
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class CreateWorktreeDialog(private val project: Project) : DialogWrapper(project) {

    private val existingBranchRadio = JBRadioButton("Existing branch", true)
    private val newBranchRadio = JBRadioButton("Create new branch", false)

    private val branchComboBox = com.intellij.openapi.ui.ComboBox<String>()
    private val newBranchField = JBTextField(20)
    private val pathField = TextFieldWithBrowseButton()
    private val openAfterCreationCheckbox = JBCheckBox("Open in IDE after creation", true)

    private val branchLabel = JBLabel("Branch:")
    private val newBranchLabel = JBLabel("New branch name:")

    private val worktreeService = WorktreeService.getInstance(project)

    val branchName: String
        get() = if (createNewBranch) newBranchField.text else branchComboBox.selectedItem as? String ?: ""

    val worktreePath: Path
        get() = Path.of(pathField.text)

    val createNewBranch: Boolean
        get() = newBranchRadio.isSelected

    val openAfterCreation: Boolean
        get() = openAfterCreationCheckbox.isSelected

    init {
        title = "Create Worktree"
        init()
        loadBranches()
        setupListeners()
        updateVisibility()
    }

    override fun createCenterPanel(): JComponent {
        ButtonGroup().apply {
            add(existingBranchRadio)
            add(newBranchRadio)
        }

        pathField.addBrowseFolderListener(
            "Select Worktree Location",
            "Choose the directory for the new worktree",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }

        // Radio buttons
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        panel.add(existingBranchRadio, gbc)

        gbc.gridy = 1
        panel.add(newBranchRadio, gbc)

        // Branch selection
        gbc.gridy = 2; gbc.gridwidth = 1
        panel.add(branchLabel, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(branchComboBox, gbc)

        // New branch name
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(newBranchLabel, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(newBranchField, gbc)

        // Path
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel("Path:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(pathField, gbc)

        // Checkbox
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2
        panel.add(openAfterCreationCheckbox, gbc)

        // Set preferred width (~30% wider than default)
        panel.preferredSize = Dimension(550, panel.preferredSize.height)

        return panel
    }

    private fun loadBranches() {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) return

        val repository = repositories.first()
        val branches = GitBranchUtil.sortBranchNames(repository.branches.localBranches.map { it.name })

        branchComboBox.removeAllItems()
        branches.forEach { branchComboBox.addItem(it) }
    }

    private fun setupListeners() {
        // Update visibility when radio buttons change
        existingBranchRadio.addActionListener {
            updateVisibility()
            updateDefaultPath()
        }
        newBranchRadio.addActionListener {
            updateVisibility()
            updateDefaultPath()
        }

        // Update path when branch changes
        branchComboBox.addActionListener { updateDefaultPath() }
        newBranchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
        })

        // Set initial path
        updateDefaultPath()
    }

    private fun updateVisibility() {
        val existing = existingBranchRadio.isSelected
        branchLabel.isVisible = existing
        branchComboBox.isVisible = existing
        newBranchLabel.isVisible = !existing
        newBranchField.isVisible = !existing
    }

    private fun updateDefaultPath() {
        val branch = if (createNewBranch) newBranchField.text else branchComboBox.selectedItem as? String
        if (!branch.isNullOrBlank()) {
            val defaultPath = worktreeService.getDefaultWorktreePath(branch)
            if (defaultPath != null) {
                pathField.text = defaultPath.toString()
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val branch = branchName
        if (branch.isBlank()) {
            return ValidationInfo(
                "Branch name is required",
                if (createNewBranch) newBranchField else branchComboBox
            )
        }

        val path = pathField.text
        if (path.isBlank()) {
            return ValidationInfo("Path is required", pathField)
        }

        val pathFile = Path.of(path)
        if (java.nio.file.Files.exists(pathFile)) {
            return ValidationInfo("Path already exists", pathField)
        }

        // Check if branch already has a worktree
        if (!createNewBranch) {
            val existingWorktrees = worktreeService.listWorktrees()
            val branchInUse = existingWorktrees.find { it.branch == branch }
            if (branchInUse != null) {
                return ValidationInfo(
                    "Branch '$branch' is already checked out in: ${branchInUse.path}",
                    branchComboBox
                )
            }
        }

        return null
    }
}
