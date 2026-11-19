# ExpressiveMenu

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21%2B-brightgreen.svg)](https://developer.android.com/about/dashboards)
[![](https://jitpack.io/v/saqib-cipher/ExpressiveMenu.svg)](https://jitpack.io/#saqib-cipher/ExpressiveMenu)


A single-file, zero-dependency popup / dropdown menu for Android built for the native View system. ExpressiveMenu brings Material 3 Expressive-inspired segmented menu rows, theme styling, optional frosted glass blur, expandable submenus, descriptions, trailing content, single-choice selection, checkboxes, custom views, spring animations, and background dimming.

---

## Features

- **Theme & Blur Styles**:
  - `STYLE_THEME` (default): Solid segments reading surface and accent colors directly from your app theme (Material You, Light & Dark modes).
  - `STYLE_BLUR`: Frosted-glass translucent segments that adapt to the theme and the content behind the menu.
- **Segmented Layout**: Material 3 Expressive layout with large outer corners, small inner corners, and spring shape morphing.
- **Rich Menu Items**: Action items, submenus, checkboxes, headers with descriptions, separators, and custom views.
- **Inline Submenus**: Tapping a submenu item smoothly expands its children inline while changing the parent into a selected pill.
- **Single Choice & Checkboxes**: Built-in support for single-choice radio selection and animated squircle checkboxes.
- **Smart Positioning & Positioning Offset**: Automatically detects screen boundaries and positions above or below the anchor view with spring entrance and quick exit animations.
- **Zero External Dependencies**: Pure Java implementation using standard Android framework APIs (Min SDK 21+). Compatible with both Java and Kotlin projects.

---

## Installation

### 1. Via JitPack

Add JitPack repository to your root `settings.gradle` (or root `build.gradle`):

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency to your app module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.cipher:ExpressiveMenu:1.0.0'
}
```

### 2. Via Project Module

If including directly in a multi-module Gradle project:

```groovy
dependencies {
    implementation project(':expressivemenu')
}
```

### 3. As a Single File

Since `ExpressiveMenu` has zero dependencies, you can also directly copy `ExpressiveMenu.java` from the `expressivemenu` module into your project and use `import com.cipher.expressivemenu.ExpressiveMenu;` or change the `package` header to match your app package!

---

## Quick Start

### Basic Usage (Java)

```java
import com.cipher.expressivemenu.ExpressiveMenu;

ExpressiveMenu menu = ExpressiveMenu.with(this)
        .setStyle(ExpressiveMenu.STYLE_THEME)
        .addHeader("Sort by", "Choose how files are ordered")
        .add(ExpressiveMenu.Item.action(1, "Name")
                .description("A to Z")
                .selected(true))
        .add(ExpressiveMenu.Item.action(2, "Date"))
        .addSeparator()
        .add(ExpressiveMenu.Item.checkbox(3, "Show hidden files", false))
        .setSingleChoice(true)
        .setBackgroundDim(0.15f)
        .setOnItemClickListener(new ExpressiveMenu.OnItemClickListener() {
            @Override
            public void onItemClick(ExpressiveMenu menu, ExpressiveMenu.Item item) {
                // Handle click using item.getId()
            }
        });

menu.show(anchorView);
```

### Frosted Glass Blur Style

```java
ExpressiveMenu.with(this)
        .setStyle(ExpressiveMenu.STYLE_BLUR)
        .setBlurRadius(22)
        .setItemOpacity(0.46f)
        .setBackgroundDim(0.12f)
        .add(ExpressiveMenu.Item.action(1, "Option"))
        .show(anchorView);
```

### Inline Submenus

```java
ExpressiveMenu.with(this)
        .add(ExpressiveMenu.Item.submenu(10, "More options")
                .description("Date, size, type")
                .child(ExpressiveMenu.Item.action(11, "Sort by Date"))
                .child(ExpressiveMenu.Item.action(12, "Sort by Size"))
                .child(ExpressiveMenu.Item.checkbox(13, "Folders first", true)))
        .show(anchorView);
```

---

## Project Structure

```text
ExpressiveMenu/
├── .github/
│   └── workflows/
│       └── release.yml          # Automated release workflow on version code change
├── app/                          # Demo application
├── expressivemenu/               # Android library module
│   ├── consumer-rules.pro
│   ├── build.gradle
│   └── src/main/java/
│       └── com/cipher/expressivemenu/
│           └── ExpressiveMenu.java
├── .gitignore                    # Version control ignore rules
├── build.gradle
├── gradle.properties             # VERSION_CODE and VERSION_NAME configuration
├── jitpack.yml                   # JitPack build configuration
├── settings.gradle
└── README.md
```

---

## Automated Releases

This repository includes a GitHub Actions workflow (`.github/workflows/release.yml`) that runs automatically when changes are pushed to `main` or `master`:

1. Reads `VERSION_CODE` and `VERSION_NAME` from `gradle.properties`.
2. Checks if a Git release tag (e.g. `v1.0.0`) exists.
3. If `VERSION_CODE` / `VERSION_NAME` is updated, the workflow automatically:
   - Builds the `.aar` library artifact and the demo `.apk`.
   - Creates and pushes a Git tag `v<VERSION_NAME>`.
   - Publishes a GitHub Release with attached `.aar` and `.apk` assets.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Author

**Cipher**
