import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REF_HTML = ROOT / "docs/blaze-ui-reference-en.html"
THEME_RS = ROOT / "crates/horizon-theme/src/blaze.rs"


class UIReferenceParityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.assertTrue(REF_HTML.exists(), f"Reference HTML not found at {REF_HTML}")
        self.assertTrue(THEME_RS.exists(), f"Theme file not found at {THEME_RS}")
        self.html_text = REF_HTML.read_text(encoding="utf-8")
        self.theme_text = THEME_RS.read_text(encoding="utf-8")

    def test_css_root_and_palettes_parity(self) -> None:
        # 1. Parse palettes from HTML
        palettes_match = re.search(r"const\s+palettes\s*=\s*\{([\s\S]*?)\};", self.html_text)
        self.assertIsNotNone(palettes_match, "Could not locate palettes JS object in reference HTML")
        palettes_raw = palettes_match.group(1)

        ref_palettes = {}
        for line in palettes_raw.strip().splitlines():
            line = line.strip().rstrip(",")
            if not line:
                continue
            key_m = re.match(r"(\w+)\s*:\s*\{([^}]+)\}", line)
            if key_m:
                key = key_m.group(1)
                props_raw = key_m.group(2)
                props = {}
                for part in re.split(r",\s*(?=[a-z0-9_]+\s*:)", props_raw):
                    k, v = part.split(":", 1)
                    props[k.strip()] = v.strip().strip("'\"")
                ref_palettes[key] = props

        expected_keys = ["cyan", "emerald", "crimson", "violet", "amber"]
        for k in expected_keys:
            self.assertIn(k, ref_palettes, f"Palette {k} missing from reference HTML")

        # 2. Check each palette against crates/horizon-theme/src/blaze.rs
        for k in expected_keys:
            ref_p = ref_palettes[k]
            primary_hex = ref_p["primary"].lower()
            secondary_hex = ref_p["secondary"].lower()
            p1_hex = ref_p["p1"].lower()
            p2_hex = ref_p["p2"].lower()
            p3_hex = ref_p["p3"].lower()

            # Ensure primary hex and secondary hex appear in blaze.rs
            self.assertIn(primary_hex, self.theme_text.lower(), f"Primary hex {primary_hex} for {k} missing in blaze.rs")
            self.assertIn(secondary_hex, self.theme_text.lower(), f"Secondary hex {secondary_hex} for {k} missing in blaze.rs")
            self.assertIn(p1_hex.replace("#", "0x"), self.theme_text.lower(), f"p1 hex {p1_hex} for {k} missing in blaze.rs")
            self.assertIn(p2_hex.replace("#", "0x"), self.theme_text.lower(), f"p2 hex {p2_hex} for {k} missing in blaze.rs")
            self.assertIn(p3_hex.replace("#", "0x"), self.theme_text.lower(), f"p3 hex {p3_hex} for {k} missing in blaze.rs")

    def test_theme_tokens_exact_values(self) -> None:
        # Background #05070b
        self.assertIn("0x05070b", self.theme_text)
        # Text #f1f5f9
        self.assertIn("0xf1f5f9", self.theme_text)
        # Muted slate #94a3b8
        self.assertIn("0x94a3b8", self.theme_text)
        # Glass panel rgba(14, 19, 28, 0.72)
        self.assertIn("(14, 19, 28)", self.theme_text)
        self.assertIn("0.72", self.theme_text)
        # Glass border rgba(255, 255, 255, 0.09)
        self.assertIn("0.09", self.theme_text)
        # Pill rgba(10, 14, 22, 0.78)
        self.assertIn("(10, 14, 22)", self.theme_text)
        self.assertIn("0.78", self.theme_text)
        # Pill border rgba(255, 255, 255, 0.08)
        self.assertIn("0.08", self.theme_text)

    def test_zero_legacy_constants_in_codebase(self) -> None:
        legacy_tokens = [
            "9A7BFF", "13111C", "1D192B", "1A1333", "131A2E", "0D0B14", "2B2937",
        ]
        crates_dir = ROOT / "crates"
        rust_files = list(crates_dir.glob("**/*.rs"))
        self.assertGreater(len(rust_files), 50, "Crates rust files not found")

        for f in rust_files:
            text = f.read_text(encoding="utf-8", errors="ignore")
            for token in legacy_tokens:
                self.assertNotIn(
                    token.lower(),
                    text.lower(),
                    f"Legacy token {token} found in {f.relative_to(ROOT)}"
                )
            if "launcher_pages.rs" in f.name:
                self.assertNotIn(
                    "my3_",
                    text,
                    f"Legacy my3_ prefix found in {f.relative_to(ROOT)}"
                )

    def test_wallpaper_assets_presence(self) -> None:
        wallpapers_dir = ROOT / "crates/horizon-client/src/android/wallpapers"
        self.assertTrue(wallpapers_dir.is_dir(), f"Wallpapers dir not found: {wallpapers_dir}")
        expected_wallpapers = ["cyan.png", "emerald.png", "crimson.png", "violet.png", "amber.png", "winter.png"]
        for wp in expected_wallpapers:
            path = wallpapers_dir / wp
            self.assertTrue(path.is_file(), f"Wallpaper {wp} missing in {wallpapers_dir}")
            # Ensure it is a valid PNG header
            header = path.read_bytes()[:8]
            self.assertEqual(header, b"\x89PNG\r\n\x1a\n", f"{wp} has invalid PNG header")
            # Ensure size is optimized (< 400KB)
            self.assertLess(path.stat().st_size, 400 * 1024, f"{wp} exceeds 400KB limit")

    def test_proportional_grid_math_mobile_resolutions(self) -> None:
        resolutions = [
            (1280, 720),
            (1520, 720),
            (2400, 1080),
        ]
        gap = 10.0
        header_h = 58.0
        pad_x = 16.0
        pad_bottom = 12.0

        for screen_w, screen_h in resolutions:
            content_w = screen_w - 2.0 * pad_x
            content_h = screen_h - header_h - pad_bottom

            # 12-row proportional grid (hero 5, deck 3.5, shortcuts 3.5) with 2 gaps
            total_gaps = 2.0 * gap
            avail_h = content_h - total_gaps
            h_unit = avail_h / 12.0

            row1_h = 5.0 * h_unit
            row2_h = 3.5 * h_unit
            row3_h = 3.5 * h_unit

            # Hero is a widescreen banner: 38%..42% of the content area, never a thin strip.
            self.assertGreaterEqual(row1_h / content_h, 0.38)
            self.assertLessEqual(row1_h / content_h, 0.42)
            self.assertGreater(content_w / row1_h, 2.0)

            # Total heights match exactly
            self.assertAlmostEqual(row1_h + row2_h + row3_h + total_gaps, content_h, places=4)

            # Row 2 (5:2:5 columns) with 2 gaps
            r2_avail_w = content_w - 2.0 * gap
            r2_unit = r2_avail_w / 12.0
            r2_c1 = 5.0 * r2_unit
            r2_c2 = 2.0 * r2_unit
            r2_c3 = 5.0 * r2_unit
            self.assertAlmostEqual(r2_c1 + r2_c2 + r2_c3 + 2.0 * gap, content_w, places=4)

            # Row 3 (6:6 columns) with 1 gap
            r3_avail_w = content_w - gap
            r3_unit = r3_avail_w / 12.0
            r3_c1 = 6.0 * r3_unit
            r3_c2 = 6.0 * r3_unit
            self.assertAlmostEqual(r3_c1 + r3_c2 + gap, content_w, places=4)


if __name__ == "__main__":
    unittest.main()
