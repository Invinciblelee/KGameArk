# KGameArk

KGameArk is a high-performance, lightweight cross-platform 2D game engine built on **Compose Multiplatform**. It combines a modern **ECS (Entity-Component-System)** architecture with the declarative UI power of Compose, providing a unified game development experience across Android, iOS, Web, and Desktop.

## 🌟 Core Features

- **Modern ECS Architecture**: Core logic modified from the open-source project [Fleks](https://github.com/Quillraven/Fleks), providing high-performance entity-component management and total decoupling of logic and data.
- **Declarative Game DSL**: Minimalist syntax for defining Scenes, Worlds, and Entities.
- **High-Performance Particle System**: Uses a mathematical computation graph, achieving high execution efficiency through pre-compiled closures.
- **Intelligent Coordinate System**: Designed with a virtual resolution where the screen center is the origin `(0, 0)`, automatically handling multi-device adaptation and Anchor transformations.
- **Deep Compose Integration**: Seamlessly blends Background/Foreground UI layers, allowing the use of standard Compose components for HUD development.
- **High-Performance Input**: Supports multi-touch and keyboard axis polling with automatic world coordinate conversion.
- **Tiled Map Support**: Native support for `.tmx` / `.json` map loading, layer rendering, and physics collision detection.

## 📐 Coordinate System

KGameArk uses a highly abstract coordinate design, which is key to understanding the engine logic:
- **World Space**: The origin `(0, 0)` is located at the center of the viewport.
- **Local Space**: Inside a `Renderable` draw block or a `Visual`, `(0, 0)` defaults to the entity's anchor center (usually `Anchor.Center`).
- **Adaptation Mechanism**: The engine automatically scales the `Density` based on the `virtualSize`. The `.dp` units used in `onForegroundUI` will result in the same visual size across all devices regardless of resolution.

## 📂 Module Structure

- `kgame`: **Engine Core**. Contains ECS scheduling, particle system, asset management, multi-platform adaptation layers, and rendering pipeline.
- `shared`: **Game Logic Layer**. This is where developers write specific game scenes, systems, and components.
- `androidApp` / `iosApp` / `desktopApp` / `webApp`: Platform-specific entry points.

## 🚀 Quick Start (DSL Example)

```kotlin
sealed class MyRoute {
    data object Menu : MyRoute()
    data object Level : MyRoute()
}

@Composable
fun MyGame() {
    val sceneStack = rememberGameSceneStack<MyRoute>(MyRoute.Menu)

    KGame(sceneStack = sceneStack, virtualSize = Size(1280f, 720f)) {
        // --- 1. Menu Scene ---
        scene<MyRoute.Menu> { _ ->
            onForegroundUI {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { sceneStack.push(MyRoute.Level) }) {
                        Text("Start Game")
                    }
                }
            }
        }

        // --- 2. Game Scene ---
        scene<MyRoute.Level> { _ ->
            onWorld(capacity = 2048) {
                useDefaultSystems() // Enable built-in systems (Physics, Render, Camera, etc.)
                configure {
                    injectables { +MyGameData() } // Dependency Injection
                    systems { +CustomSystem() }   // Register custom logic systems
                }
                spawn {
                    entity {
                        +Transform(position = Offset(0f, 0f)) // (0,0) is the center of the world
                        +Renderable(CircleVisual(size = 40f, color = Color.Cyan))
                    }
                }
            }

            onUpdate { dt ->
                // Global logic update
                if (input.isKeyJustPressed(Key.Escape)) sceneStack.pop()
            }

            onForegroundUI {
                // Develop HUD with Compose, auto-adapted to virtual resolution
                Text("Score: ${data.score}", modifier = Modifier.padding(16.dp), color = Color.Yellow)
            }
        }
    }
}
```

## ⚠️ Important Notes

- **Material System**: Due to underlying SkiaSL/AGSL limitations, the **Material effect system is not supported on Android versions below 13 (API 33)**. On older Android devices, the engine will automatically fallback to basic rendering modes.

## 🛠 Build & Run

### Android
```shell
./gradlew :androidApp:assembleDebug
```

### Desktop (JVM)
```shell
./gradlew :desktopApp:run
```

### Web (Wasm/JS)
```shell
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

### iOS
Open `iosApp/iosApp.xcworkspace` in Xcode or run:
```shell
./gradlew :iosApp:iosDeploy
```

## 📄 License

KGameArk is licensed under the [Apache License 2.0](LICENSE). This includes the core ECS logic, which is a modified version of the [Fleks](https://github.com/Quillraven/Fleks) project (Copyright (c) 2021-2023 Quillraven).

---
Powered by **Compose Multiplatform** & **Kotlin Multiplatform**.
Special thanks to the [Fleks](https://github.com/Quillraven/Fleks) project for the ECS architecture inspiration.

    