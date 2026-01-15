plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Use Java toolchain for consistent JDK version
// Must target JDK 21 as that's what IntelliJ IDEA 2025.3 uses internally
kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3.1")

        // Git4Idea bundled plugin for Git integration
        bundledPlugin("Git4Idea")

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")

        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }

        description = """
            <p>Manage Git worktrees directly from IntelliJ IDEA.</p>

            <h3>Features</h3>
            <ul>
                <li><b>Create Worktrees</b> - Create new worktrees from existing or new branches with customizable paths</li>
                <li><b>Open Worktrees</b> - Open worktrees in new IDE windows with status indicators</li>
                <li><b>Switch Between Worktrees</b> - Quick submenu to jump between worktree projects</li>
                <li><b>Remove Worktrees</b> - Safe removal with real-time dirty/unpushed commit warnings</li>
                <li><b>Project Indicator</b> - See which worktree you're in via project name decoration</li>
                <li><b>Folder Decorations</b> - Worktree directories display a green branch icon with branch name</li>
                <li><b>Context Menu Actions</b> - Right-click worktree folders to open or remove them directly</li>
            </ul>

            <h3>Usage</h3>
            <p><b>Git Menu:</b> Access worktree actions from <b>Git → Worktrees</b>:</p>
            <ul>
                <li><b>Create Worktree...</b> - Opens dialog to create a new worktree</li>
                <li><b>Open Worktree...</b> - Lists existing worktrees to open in a new window</li>
                <li><b>Remove Worktree...</b> - Safely remove worktrees with status checks</li>
                <li><b>Switch to Worktree</b> - Submenu showing sibling worktrees for quick switching</li>
            </ul>

            <p><b>Context Menu:</b> Right-click on worktree folders in the Project view:</p>
            <ul>
                <li><b>Open Worktree in New Window</b> - Opens the worktree in a new IDE window</li>
                <li><b>Remove Worktree</b> - Removes the worktree with safety checks</li>
            </ul>

            <h3>Visual Indicators</h3>
            <ul>
                <li>Worktree directories show a green branch icon with the branch name</li>
                <li>Project root displays <code>[worktree: branch-name]</code> when opened as a worktree</li>
            </ul>

            <h3>Settings</h3>
            <p>Configure plugin behavior in <b>Settings → Version Control → Worktree Manager</b>.</p>
        """.trimIndent()

        changeNotes = """
            <h3>0.1.6</h3>
            <ul>
                <li>Updated .gitignore with plugin signing credential patterns</li>
            </ul>
            <h3>0.1.4</h3>
            <ul>
                <li>Added unit tests for WorktreeInfo data class and git worktree output parsing</li>
            </ul>
            <h3>0.1.3</h3>
            <ul>
                <li>Replace deprecated APIs with modern alternatives</li>
            </ul>
            <h3>0.1.2</h3>
            <ul>
                <li>Refactored package hierarchy to org.metastacks</li>
            </ul>
            <h3>0.1.0</h3>
            <ul>
                <li>Initial release</li>
                <li>Create, open, switch, and remove worktrees from Git menu</li>
                <li>Project name decoration for worktree indicator</li>
                <li>Worktree folder decorations with green branch icon</li>
                <li>Context menu actions for worktree folders</li>
                <li>Real-time dirty/unpushed status checks before removal</li>
                <li>Settings panel under Version Control</li>
            </ul>
        """.trimIndent()
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
