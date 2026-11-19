# ExpressiveMenu

A single-file, zero-dependency popup/dropdown menu for Android using the native View system. It provides Material 3 Expressive-inspired segmented menu rows, theme styling, optional frosted blur, submenus, descriptions, trailing content, selection, checkboxes, custom views, animations, and screen dimming.


## Features

- `STYLE_THEME` and `STYLE_BLUR`
- Theme-aware colors
- Optional backdrop blur
- Configurable item opacity and background dim
- Single-choice selection and checkbox items
- Inline expandable submenus
- Header, descriptions, separators, icons, trailing text/icons, and custom views
- Animated menu entrance, exit, row state, and submenu expansion
- Smart positioning relative to the anchor
- No external dependencies
- Minimum Android API 21


## Installation

### Gradle module

Add the `expressivemenu` module to your project and depend on it:


```gradle
dependencies {
    implementation project(':expressivemenu')
}
```


### JitPack (after publishing a GitHub release/tag)

1. Push this repository to GitHub.
2. Create a release/tag, for example `1.0.0`.
3. Add JitPack to your project's repositories:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

4. Add the dependency (replace `OWNER` with your GitHub username/organization):

```gradle
 dependencies {
     implementation 'com.github.OWNER.ExpressiveMenu:expressivemenu:1.0.0'
 }
```

Because this repository contains multiple Gradle modules, the dependency coordinate includes the module name. JitPack build availability depends on your repository and tag configuration; check the JitPack build page after tagging.


## Basic usage

```java
ExpressiveMenu menu = ExpressiveMenu.with(this)
        .setStyle(ExpressiveMenu.STYLE_THEME)
        .addHeader("Sort by", "Choose how files are ordered")
        .add(ExpressiveMenu.Item.action(1, "Name")
                .description("A to Z")
                .selected(true))
        .add(ExpressiveMenu.Item.action(2, "Date"))
        .add(ExpressiveMenu.Item.checkbox(3, "Show hidden files", false))
        .setSingleChoice(true)
        .setBackgroundDim(0.15f)
        .setOnItemClickListener(new ExpressiveMenu.OnItemClickListener() {
            @Override
            public void onItemClick(ExpressiveMenu menu, ExpressiveMenu.Item item) {
                // Handle selection using item.getId()
            }
        });

menu.show(anchorView);
```

### Blur style

```java
ExpressiveMenu.with(this)
        .setStyle(ExpressiveMenu.STYLE_BLUR)
        .setBlurRadius(22)
        .setItemOpacity(0.46f)
        .setBackgroundDim(0.12f)
        .add(ExpressiveMenu.Item.action(1, "Option"))
        .show(anchorView);
```

### Background dim

```java
.setBackgroundDim(0f)    // no dim
.setBackgroundDim(0.15f)  // 15% dim
.setBackgroundDim(1f)    // maximum dim
```

## Demo app

The repository includes a simple demo app. Open the project in Android Studio and run the `app` configuration.


## Project structure

```text
ExpressiveMenu/
├── app/                         # Demo application
├── expressivemenu/               # Android library module
│   └── src/main/java/
│       └── com/grafixlab/expressivemenu/
│           └── ExpressiveMenu.java
├── .github/workflows/android.yml   # CI build workflow
├── build.gradle
├── settings.gradle
└── README.md
```

## License

MIT License. See [LICENSE](LICENSE).

## Author

GrafixLab
