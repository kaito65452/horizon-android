import os
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AndroidLauncherOverhaulTests(unittest.TestCase):
    def test_legacy_launcher_pages_deleted(self) -> None:
        """Verify that legacy Rust launcher UI files are permanently deleted."""
        legacy_paths = [
            ROOT / "crates/horizon-client/src/android/launcher_pages.rs",
            ROOT / "crates/horizon-client/src/android/launcher.rs",
            ROOT / "crates/horizon-client/src/android/blaze_ui",
            ROOT / "crates/horizon-client/src/android/chooser.rs",
            ROOT / "crates/horizon-client/src/android/login.rs",
            ROOT / "crates/horizon-client/src/android/wardrobe.rs",
        ]
        for p in legacy_paths:
            self.assertFalse(p.exists(), f"Legacy Rust launcher file {p.name} must be permanently deleted")

    def test_pure_rust_jni_bridge_exists(self) -> None:
        """Verify that jni_bridge.rs exists and exposes required JNI bindings."""
        bridge_path = ROOT / "crates/horizon-client/src/android/jni_bridge.rs"
        self.assertTrue(bridge_path.is_file(), "jni_bridge.rs must exist")
        content = bridge_path.read_text(encoding="utf-8")
        
        required_symbols = [
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeInit",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeAreAssetsReady",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetConfigJson",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSaveConfigJson",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeLaunchGame",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeStartMultiThreadDownload",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetDownloadProgressJson",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSetKeepAliveEnabled",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSetComplexVoiceChatConfig",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeFetchCatalogMods",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetControlLayoutJson",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSaveControlLayoutJson",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeExportWorlds",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetDiagnosticsJson",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetVersion",
            "Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeResetSettings",
        ]
        for sym in required_symbols:
            self.assertIn(sym, content, f"jni_bridge.rs must export {sym}")

    def test_kotlin_compose_frontend_files_exist(self) -> None:
        """Verify that lightweight Compose frontend files are present in packaging/android/."""
        pkg_base = ROOT / "packaging/android/java/dev/horizon/android"
        expected_files = [
            pkg_base / "bridge/BlazeNativeBridge.kt",
            pkg_base / "service/BlazeKeepAliveService.kt",
            pkg_base / "ui/theme/BlazeTheme.kt",
            pkg_base / "ui/components/BlazeComponents.kt",
            pkg_base / "ui/topbar/BlazeTopBar.kt",
            pkg_base / "ui/views/HomeView.kt",
            pkg_base / "ui/views/DownloadsView.kt",
            pkg_base / "ui/views/BrowseView.kt",
            pkg_base / "ui/views/ManagerView.kt",
            pkg_base / "ui/views/SkinStudioView.kt",
            pkg_base / "ui/views/GoyControlEditorView.kt",
            pkg_base / "ui/views/SettingsView.kt",
            pkg_base / "ui/BlazeLauncherActivity.kt",
        ]
        for f in expected_files:
            self.assertTrue(f.is_file(), f"Expected Kotlin Compose file missing: {f}")

    def test_no_material3_bloat_in_compose_ui(self) -> None:
        """Verify that Material 3 is strictly forbidden and absent from packaging/android/."""
        build_gradle = (ROOT / "packaging/android/build.gradle").read_text(encoding="utf-8")
        self.assertNotIn("material3", build_gradle.lower(), "packaging/android/build.gradle must not include material3")

        pkg_base = ROOT / "packaging/android/java/dev/horizon/android"
        for kt_file in pkg_base.rglob("*.kt"):
            content = kt_file.read_text(encoding="utf-8")
            self.assertNotIn(
                "androidx.compose.material3",
                content,
                f"File {kt_file.name} must not import Material 3"
            )

    def test_complex_voice_chat_attribution_present(self) -> None:
        """Verify that 'Complex Voice Chat (ported by Asano Saryu)' is consistently used."""
        expected_label = "Complex Voice Chat (ported by Asano Saryu)"
        
        # Check inventory
        inventory = (ROOT / "docs/ui-port-inventory.md").read_text(encoding="utf-8")
        self.assertIn(expected_label, inventory)
        
        # Check Compose Settings
        settings_view = (ROOT / "packaging/android/java/dev/horizon/android/ui/views/SettingsView.kt").read_text(encoding="utf-8")
        self.assertIn(expected_label, settings_view)

        # Check Visual Reference HTML
        blaze_html = (ROOT / "docs/BlazeLauncher.html").read_text(encoding="utf-8")
        self.assertIn(expected_label, blaze_html)

    def test_android_manifest_configuration(self) -> None:
        """Verify AndroidManifest has sensorLandscape and BlazeKeepAliveService."""
        manifest = (ROOT / "packaging/android/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("android:screenOrientation=\"sensorLandscape\"", manifest)
        self.assertIn("dev.horizon.android.ui.BlazeLauncherActivity", manifest)
        self.assertIn("dev.horizon.android.service.BlazeKeepAliveService", manifest)
        self.assertIn("android.permission.FOREGROUND_SERVICE", manifest)


if __name__ == "__main__":
    unittest.main()
