# Blaze Launcher Naming Map (Canonical Reference)

This map defines the mapping between legacy/prompt Horizon names and the current canonical Blaze Launcher architecture. **Sub-agents and contributors must consult this map prior to modifying any file.**

---

## 1. Core Component Mapping

| Category | Legacy / Prompt Name | Current Canonical Name | Source File / Path | Notes |
|---|---|---|---|---|
| **Game Activity** | `HorizonActivity.java` | `dev.horizon.android.HorizonActivity` | `packaging/android/java/dev/horizon/android/HorizonActivity.java` | Extends `NativeActivity`. Declared in manifest; loads `libhorizon_android.so`. |
| **Native Game Engine Library** | `libhorizon_android.so` | `horizon_android` | `crates/horizon-client/` | Declared in manifest as `<meta-data android:name="android.app.lib_name" android:value="horizon_android" />`. Must NOT be renamed. |
| **Keep-Alive Service** | `BlazeKeepAliveService` | `dev.horizon.android.service.BlazeKeepAliveService` | `packaging/android/java/dev/horizon/android/service/BlazeKeepAliveService.kt` | Foreground service for data sync, background downloads, and Complex Voice Chat. |
| **Launcher Activity** | `HorizonLauncherActivity` | `dev.horizon.android.ui.BlazeLauncherActivity` | `packaging/android/java/dev/horizon/android/ui/BlazeLauncherActivity.kt` | Main launcher UI activity using Jetpack Compose. |
| **Launcher Theme** | `horizon-theme` / `HorizonTheme` | `BlazeTheme` / `LocalBlazeColors` | `packaging/android/java/dev/horizon/android/ui/theme/BlazeTheme.kt` | Theme tokens (`BlazeColors`, `BlazePalette`) with reactive `ThemeState`. |
| **Native JNI Bridge** | `HorizonNativeBridge` | `dev.horizon.android.bridge.BlazeNativeBridge` | `packaging/android/java/dev/horizon/android/bridge/BlazeNativeBridge.kt` | Native calls match `Java_dev_horizon_android_bridge_BlazeNativeBridge_*` in `jni_bridge.rs`. |
| **Documents Provider** | `HorizonDocumentsProvider` | `dev.horizon.android.HorizonDocumentsProvider` | `packaging/android/java/dev/horizon/android/HorizonDocumentsProvider.java` | Document provider for SAF storage export and import. |
| **Browse / Catalog** | "Horizon catalog" / `BrowseScreen` | `BrowseView` | `packaging/android/java/dev/horizon/android/ui/views/BrowseView.kt` | Modrinth & Horizon catalog browser with filters, paging, and download queueing. |
| **Wardrobe / Skin** | `WardrobeScreen` | `SkinStudioView` | `packaging/android/java/dev/horizon/android/ui/views/SkinStudioView.kt` | 3D character viewport, Microsoft/Ely.by/Mojang skin fetcher, SAF import. |
| **Downloads Queue** | `DownloadsScreen` | `DownloadsView` | `packaging/android/java/dev/horizon/android/ui/views/DownloadsView.kt` | Multi-threaded genuine telemetry download queue. |
| **Home Screen** | `HomeScreen` | `HomeView` | `packaging/android/java/dev/horizon/android/ui/views/HomeView.kt` | Home screen matching `docs/BlazeLauncher.html` visual reference. |
| **Settings Screen** | `SettingsScreen` | `SettingsView` | `packaging/android/java/dev/horizon/android/ui/views/SettingsView.kt` | Launcher settings with reactive sliders, palette selector, and About Diagnostics. |
| **Controls Editor** | `ControlEditorView` | `GoyControlEditorView` | `packaging/android/java/dev/horizon/android/ui/views/GoyControlEditorView.kt` | Touch controls and layout editor. |
| **Voice Chat** | Voice chat subsystem | Complex Voice Chat (ported by Asano Saryu) | Subtitle: "Native 3D proximity UDP voice chat with RNNoise & AGC (ported by Asano Saryu)" | Canonical name per project specification. |

---

## 2. Integrity & Consistency Check (Leftovers Audit)

1. **JNI Function Bindings:**
   - JNI function exports in `crates/horizon-client/src/android/jni_bridge.rs` specifically target `Java_dev_horizon_android_bridge_BlazeNativeBridge_*`. These are verified to match `BlazeNativeBridge.kt` package `dev.horizon.android.bridge`.
   - Native document results in `crates/horizon-client/src/android/documents.rs` target `Java_dev_horizon_android_HorizonActivity_nativeDocumentResult`, matching `dev.horizon.android.HorizonActivity`.
   - Storage provider calls in `crates/horizon-client/src/android/document_provider.rs` target `Java_dev_horizon_android_HorizonDocumentsProvider_*`, matching `dev.horizon.android.HorizonDocumentsProvider`.
2. **Application ID & Namespace:**
   - Gradle namespace: `dev.horizon.android`.
   - Default Application ID: `dev.horizon.android` (or `dev.horizon.android.dev` for debug/dev channel).
   - Manifest package matches.
3. **No Phantom Classes:**
   - All activities, services, and providers registered in `AndroidManifest.xml` exist and resolve cleanly.
