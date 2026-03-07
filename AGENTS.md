# AGENTS.md

## Cursor Cloud specific instructions

This is a Kotlin Multiplatform Compose project (Jelly Match game) targeting Android, iOS, Desktop (JVM), and Web (JS/WasmJS). No backend services, databases, or Docker are needed.

### Environment variables

The following must be set (already configured in `~/.bashrc` by the update script):

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
- `ANDROID_HOME=$HOME/android-sdk`

### Running the desktop (JVM) app

The VM lacks hardware GPU. You **must** set `SKIKO_RENDER_API=SOFTWARE` before launching the desktop app:

```
SKIKO_RENDER_API=SOFTWARE ./gradlew :composeApp:run
```

### Key Gradle commands

See `README.md` for the full list. Summary:

| Task | Command |
|---|---|
| Desktop run | `SKIKO_RENDER_API=SOFTWARE ./gradlew :composeApp:run` |
| Web (JS) dev | `./gradlew :composeApp:jsBrowserDevelopmentRun` |
| Web (Wasm) dev | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Android APK | `./gradlew :androidApp:assembleDebug` |
| JVM tests | `./gradlew :composeApp:jvmTest` |
| Lint | `./gradlew :composeApp:lint` |

### Gotchas

- The foojay toolchain resolver plugin auto-downloads the JDK if needed, but the Android SDK must be installed manually (see update script).
- Kotlin 2.3.0 + AGP 8.11.2 emits a deprecation warning about `com.android.library` plugin compatibility — this is expected and can be ignored.
- First Gradle build downloads ~1 GB of dependencies and takes several minutes; subsequent builds are cached.
