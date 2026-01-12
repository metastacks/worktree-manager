# Worktree Manager - Marketplace Publishing Plan

## Overview

Prepare the Worktree Manager plugin for publication on the JetBrains Marketplace.

## Decisions

| Decision | Choice |
|----------|--------|
| License | Apache 2.0 |
| Icon style | Tree with branches |
| Icon color | Green (#4CAF50) |
| Plugin signing | Skip (free open-source plugin) |
| README focus | End users (installation, features, usage) |

## Files to Create

### 1. License File (`LICENSE`)
- Apache 2.0 full license text
- Copyright holder: Metastacks LLC

### 2. Plugin Icons (`src/main/resources/META-INF/`)
- `pluginIcon.svg` - 40x40px green tree for light themes
- `pluginIcon_dark.svg` - lighter green variant for dark themes

### 3. README (`README.md`)
- Hero section with plugin name
- Feature list
- Installation instructions (marketplace + manual)
- Usage section
- License badge

### 4. Build Configuration (`build.gradle.kts`)
Add publishing block:
```kotlin
intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
```

## Implementation Order

1. License file
2. Plugin icons (light + dark)
3. Build config update
4. README
5. Verify & test (`./gradlew verifyPlugin`)
6. Publish (`./gradlew publishPlugin`)

## Publishing Workflow

```bash
# Build distribution zip
./gradlew buildPlugin

# Verify plugin compatibility
./gradlew verifyPlugin

# Publish to marketplace
./gradlew publishPlugin
```

## Post-Publish

- First submission requires manual JetBrains review (24-72 hours)
- Plugin URL: `plugins.jetbrains.com/plugin/<id>/worktree-manager`
- Subsequent updates publish faster
