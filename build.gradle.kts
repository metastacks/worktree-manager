plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
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
kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3")

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
            <ul>
                <li>Create new worktrees from existing or new branches</li>
                <li>Open worktrees in new IDE windows</li>
                <li>Switch between worktree projects</li>
                <li>Remove worktrees with safety checks</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <h3>0.1.0</h3>
            <ul>
                <li>Initial release</li>
                <li>Create, open, switch, and remove worktrees</li>
                <li>Project name decoration for worktree indicator</li>
            </ul>
        """.trimIndent()
    }
}
