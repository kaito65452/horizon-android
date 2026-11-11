# Zalith Launcher 2 (ZL2) Architecture & Porting Map

**Source of Truth Repository:** `/data/data/com.termux/files/home/zalith2` (GitHub: `https://github.com/ZalithLauncher/ZalithLauncher2`)
**License:** GNU General Public License v3.0 (GPL-3.0). Horizon Android uses MIT. Inclusion of GPL-3.0 code makes the resulting Android packaging derivative work subject to GPL-3.0 terms with attribution in About > Licenses.

---

## 1. Browse and Download
* **ZL2 Source Paths:**
  - Clients & APIs:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/addon/platform/modrinth/` (`ModrinthAPI.kt`, `ModrinthSearchRequest.kt`, `ModrinthSearchResult.kt`, `ModrinthSearcher.kt`, `models/*`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/addon/platform/curseforge/` (`CurseForgeAPI.kt`, `CurseForgeSearchRequest.kt`, `CurseForgeSearchResult.kt`, `CurseForgeSearcher.kt`, `models/*`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/addon/platform/` (`Platform.kt`, `PlatformClasses.kt`, `PlatformSearchFilter.kt`, `PlatformSearchResult.kt`, `PlatformSortField.kt`)
  - Download Engine & Installers:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/` (`BatchDownloader.kt`, `DownloadEngine.kt`, `DownloadRequest.kt`, `DownloadStats.kt`, `FileDownloader.kt`, `FileSink.kt`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/addon/_Download.Single.Tasks.kt`
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/install/` (`ModDownloader.kt`, `ModPackInstaller.kt`)
  - Screens:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/addon/search/SearchAddonScreen.kt`
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/addon/elements/` (`AddonPage.kt`, `_Search.Result.kt`, `_Search.Filter.kt`, `DownloadAddonElements.kt`)
* **Libraries (from libs.versions.toml):**
  - `ktor-client-core:3.6.1`, `ktor-client-okhttp:3.6.1`, `ktor-client-content-negotiation:3.6.1`, `ktor-serialization-kotlinx-json:3.6.1`
  - `okhttp:5.5.1`, `okio:3.18.2`, `gson:2.15.0`, `kotlinx-coroutines-android:1.11.0`
  - `coil-compose:3.6.3`
* **Assets & Resources:**
  - `ZalithLauncher/src/main/res/drawable/img_platform_modrinth.png`
  - `ZalithLauncher/src/main/res/drawable/img_platform_curseforge.png`
  - `ZalithLauncher/src/main/assets/addon/mod.csv`, `modpack.csv`
* **Manifest Permissions:**
  - `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`

---

## 2. Accounts and Wardrobe
* **ZL2 Source Paths:**
  - Account Models & Manager:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/account/` (`Account.kt`, `AccountType.kt`, `AccountsManager.kt`, `AccountUtils.kt`, `AccountDao.kt`)
  - Microsoft Auth & Token Refresh:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/account/microsoft/` (`MicrosoftAuthenticator.kt`, `AuthType.kt`, `models/MicrosoftXboxAuthModels.kt`, `models/MinecraftAuthResponse.kt`)
  - Yggdrasil & Profile Fetch:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/account/yggdrasil/` (`PlayerProfile.kt`, `YggdrasilApi.kt`)
  - Skin / Cape Fetch & Local Handling:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/account/wardrobe/` (`SkinFileDownloader.kt`, `CapeFileDownloader.kt`, `WardrobeDownloader.kt`, `LocalSkinUtils.kt`, `SkinModelType.kt`, `SkinType.kt`)
  - Screens:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/AccountManageScreen.kt`
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/AccountElements.kt`
* **Libraries (from libs.versions.toml):**
  - `ktor-client-core:3.6.1`, `ktor-client-okhttp:3.6.1`, `gson:2.15.0`, `commons-io:2.22.0`, `commons-codec:1.23.0`
* **Assets & Resources:**
  - Default Skins: `ZalithLauncher/src/main/assets/skin/steve.png`, `alex.png`
* **Manifest Permissions:**
  - `android.permission.INTERNET`

---

## 3. 3D Model Renderer (PetLib & View)
* **ZL2 Source Paths:**
  - Native & Core:
    - `PetLib/rust/src/` (`render.rs`, `skin.rs`, `pose.rs`, `clip.rs`, `clip_data.rs`, `clip_parity.rs`, `animation.rs`, `lighting.rs`, `pool.rs`, `api.rs`, `jni.rs`, `lib.rs`)
    - `PetLib/src/main/java/com/movtery/petlib/` (`PetLibNative.java`, `PetController.kt`, `PetPreview.kt`, `ArmModel.kt`, `PetAnimation.kt`, `PetGazeRouter.kt`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/_PetLib.kt`
  - Fallback / Pure Canvas 3D Geometry:
    - Complete 3D cuboid projection pipeline with z-sorting, dual-layer inflation, and rotation in `SkinStudioView.kt`.
* **Libraries:**
  - `glam:0.30`, `image:0.25`, `jni:0.21` (Rust crate dependencies in `PetLib/rust/Cargo.toml`)
  - Android Compose foundation, graphics, UI
* **Assets & Resources:**
  - `PetLib/tools/models/classic-player.gltf`, `PetLib/tools/models/slim-player.gltf`
  - `packaging/android/assets/skin/steve.png`, `alex.png`
* **Manifest / JNI:**
  - `libpetlib.so` (built via cargo-ndk) or embedded projection engine.

---

## 4. Theme, Background & Settings Wiring
* **ZL2 Source Paths:**
  - Theme Engine:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/` (`ColorTheme.kt`, `Theme.kt`, `NativeThemeUtils.kt`)
    - `ColorPicker/src/main/kotlin/com/movtery/colorpicker/`
  - Background & Blur:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/viewmodel/` (`BackgroundViewModel.kt`, `BackgroundGlassBlur.kt`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/setting/enums/BackgroundBlur.kt`
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/BackgroundCard.kt`
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/BackgroundGlass.kt`
* **Libraries (from libs.versions.toml):**
  - `androidx-media3-exoplayer:1.11.1`, `androidx-media3-ui:1.11.1`
  - `materialKolor:5.0.1`, `material-color-utilities:5.0.1`
* **Assets & Resources:**
  - Video and image background files stored locally via SAF picker into app files directory.
* **Manifest Permissions:**
  - `android.permission.READ_MEDIA_IMAGES`, `android.permission.READ_MEDIA_VIDEO` (SAF OpenDocument URI persistable permissions)

---

## 5. Game Process & Lifecycle
* **ZL2 Source Paths:**
  - Process & Exit Hook:
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/bridge/ZLNativeInvoker.kt` (`jvmExit()`, `killProgress()`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/utils/LocalUtils.kt` (`killProgress() -> Process.killProcess(Process.myPid())`)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameService.kt` (Foreground keep-alive service)
    - `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/game/elements/ForceCloseDialog.kt`
    - `ZalithLauncher/src/main/jni/exit_hook.c`
* **Libraries:**
  - `androidx-core-ktx:1.15.0`
* **Manifest Entries:**
  - `dev.horizon.android.HorizonActivity`
  - `dev.horizon.android.service.BlazeKeepAliveService` with `foregroundServiceType="dataSync|microphone"`
  - `android.permission.FOREGROUND_SERVICE`, `android.permission.WAKE_LOCK`
