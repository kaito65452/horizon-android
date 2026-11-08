# UI/UX Parity & Adaptation Validation Report
**Project:** Horizon × Zalith Launcher 2  
**Target:** Launcher Interface, Navigation Architecture, and Settings Design  
**Branch:** `exp/zalith-1-port`  
**Date:** 2026-11-08  
**Auditor:** Principal Android Architect & UX Systems Engineer  

---

## 1. Executive Summary

This report evaluates the UI/UX adaptation of **Zalith Launcher 2 (ZL2)** into **Horizon-Android**. 

The goal was to transform Horizon's interface from a utilitarian diagnostic shell into a complete, modern Android gaming launcher inspired by Zalith Launcher 2's Material 3 design philosophy, fluid navigation, and responsive ergonomics, while running entirely within Horizon's lightweight, GPU-accelerated immediate/batch presentation pipeline.

---

## 2. Component-by-Component Parity Audit

### 2.1 Navigation Architecture
- **Reference (Zalith Launcher 2):** AndroidX Navigation3 (`1.2.0`), `ScreenBackStackViewModel`, top navigation bar on tablet / landscape, bottom navigation bar on compact portrait.
- **Horizon Implementation:** [`crates/horizon-client/src/android/catalog.rs`](file:///home/saryu/horizon-android/crates/horizon-client/src/android/catalog.rs)
  - `Page` enum routing: `Launcher`, `Versions`, `Controls`, `Settings`, `Accounts`, `Logs`.
  - Backstack management with history traversal and back-button interception.
- **Verification:** `tests/test_android_launcher_overhaul.py` (`test_catalog_pages_and_navigation`)
- **Status:** **PASS**

### 2.2 Main Launcher Screen
- **Reference (Zalith Launcher 2):** Large Hero banner with game art, active profile/version card, prominent "Quick Play" FAB button, news/status feed, and system info footer.
- **Horizon Implementation:** [`crates/horizon-client/src/android/launcher_pages.rs`](file:///home/saryu/horizon-android/crates/horizon-client/src/android/launcher_pages.rs)
  - Hero layout with dynamic title typography.
  - Active version card showing Java Edition 26.3 build and graphics backend (Vulkan 1.3 / GLES 3).
  - One-tap "Launch Horizon" button triggering native session initialization.
- **Verification:** Verified via test harness and catalog draw pass.
- **Status:** **PASS**

### 2.3 Settings Interface
- **Reference (Zalith Launcher 2):** Categorized sidebar navigation (General, Video, Controls, Sound, Java/Runtime, About).
- **Horizon Implementation:** [`crates/horizon-client/src/android/settings.rs`](file:///home/saryu/horizon-android/crates/horizon-client/src/android/settings.rs)
  - Categories: `Overview`, `Video & Graphics` (Vulkan vs GLES, Render Scale, Present Mode, VSync), `Controls` (Touch Layout selection, Gyro, Vibration), `Theme & Typography`, `Storage & Scoped Access`.
  - **JVM Settings Purged:** The obsolete "Java Runtime", "JVM Args", and "Memory Allocator" tabs from ZL2 were intentionally excluded and replaced with native Engine Memory and Render Scale controls.
- **Verification:** `test_settings_categories_and_persistence` in test suite.
- **Status:** **PASS**

### 2.4 Version Management & Catalog
- **Reference (Zalith Launcher 2):** Multi-version downloader fetching Mojang version manifests, fabric/forge loaders, and client jars.
- **Horizon Implementation:** Version manager pinned to Horizon's optimized engine versions (Minecraft 26.3 native parity). Does not download obsolete Java jars.
- **Status:** **PARTIAL** (Intentionally constrained: Horizon targets native Minecraft 26.3; arbitrary legacy Java versions 1.7-1.16 are not applicable to the native Rust engine).

### 2.5 In-Game Touch Control Editor
- **Reference (Zalith Launcher 2):** `ControlEditorActivity.kt` with draggable buttons, bounding boxes, property drawers, and style pickers.
- **Horizon Implementation:** [`crates/horizon-client-input/src/control_layout/editor.rs`](file:///home/saryu/horizon-android/crates/horizon-client-input/src/control_layout/editor.rs)
  - Full-screen in-game overlay accessible via the top floating menu button or Settings.
  - Interactive selection outline, 4-corner resize handles, magnetic snapping to grid (100-unit steps) and screen boundaries, button addition, layer management, and style dialogs.
- **Verification:** `test_editor_selection_drag_snap_and_resize` passed in `horizon-client-input`.
- **Status:** **PASS**

### 2.6 Responsive Layout & Orientation Handling
- **Reference (Zalith Launcher 2):** Adaptive layouts adjusting between portrait phone and landscape/tablet surfaces.
- **Horizon Implementation:**
  - `AndroidManifest.xml` configures `sensorLandscape` for gameplay.
  - Dynamic scaling scales UI elements according to physical DPI and viewport dimensions (`scale_matches_the_320_by_240_rule`).
  - Margins and safe areas respect display cutouts and notch insets.
- **Verification:** `window::tests::scale_matches_the_320_by_240_rule` and `touch::tests::oversized_controls_can_be_dragged_on_narrow_portrait_surfaces`.
- **Status:** **PASS**

---

## 3. Parity Scorecard

| Screen / Feature | Reference Source | Horizon Adaptation | Parity Rating | Notes |
|---|---|---|---|---|
| **Top Navigation** | Zalith M3 Navigation3 | `catalog.rs` Navigation Engine | **PASS** | Responsive tab bar |
| **Hero & Quick Play** | `MainScreen.kt` | `launcher_pages.rs::draw_launcher` | **PASS** | Direct native game launch |
| **Settings Navigation** | `SettingsScreen.kt` | `settings.rs` | **PASS** | M3 category tabs |
| **Video Settings** | ZL2 Custom Video Options | Horizon wgpu/Vulkan options | **PASS** | Enhanced with native render scaling |
| **JVM Settings** | ZL2 Java Manager | *Intentionally Omitted* | **N/A** | JVM eliminated from architecture |
| **Control Map Editor** | `ControlEditorActivity` | `control_layout/editor.rs` | **PASS** | Frame-exact real-time editor |
| **Account Management** | Microsoft / Offline Auth | Offline / Horizon Profile | **PARTIAL** | MSA login in dedicated launcher module |
| **External File Picker**| Storage Access Framework | `HorizonDocumentsProvider` | **PASS** | Scoped Storage compliant |

---

## 4. Identified Gaps & Future Refinements

1. **Online Account Syncing:** Account management currently provides robust offline / local user profiles; full Microsoft OAuth flow is handled in the separate launcher desktop crate and requires porting to Android Custom Tabs.
2. **Community Preset Repository:** Pre-packaged community control layouts are bundled in assets; cloud repository browsing is slated for Phase 3.

---

## 5. Conclusion

The UI/UX adaptation successfully captures the visual elegance, intuitive navigation, and rich control customization of Zalith Launcher 2 while adapting it seamlessly into Horizon's native rendering architecture.
