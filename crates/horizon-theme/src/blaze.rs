//! Blaze Launcher design tokens from `docs/blaze-ui-reference-en.html`.
//! Landscape-first layout, glassmorphic panels, and 5 curated accent palettes.

/// The 5 Blaze Accent Palettes from `docs/blaze-ui-reference-en.html`:
/// cyan: primary #38bdf8, secondary #0284c7, glow rgba(56,189,248,0.35), soft rgba(56,189,248,0.15)
/// emerald: primary #34d399, secondary #059669, glow rgba(52,211,153,0.35), soft rgba(52,211,153,0.15)
/// crimson: primary #fb7185, secondary #e11d48, glow rgba(251,113,133,0.35), soft rgba(251,113,133,0.15)
/// violet: primary #a78bfa, secondary #7c3aed, glow rgba(167,139,250,0.35), soft rgba(167,139,250,0.15)
/// amber: primary #fbbf24, secondary #d97706, glow rgba(251,191,36,0.35), soft rgba(251,191,36,0.15)
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum AccentPalette {
    #[default]
    Cyan = 0,
    Emerald = 1,
    Crimson = 2,
    Violet = 3,
    Amber = 4,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PaletteValues {
    pub primary: u32,
    pub secondary: u32,
    pub glow: (u8, u8, u8, f32),
    pub soft: (u8, u8, u8, f32),
    pub p1: u32,
    pub p2: u32,
    pub p3: u32,
}

impl AccentPalette {
    pub const ALL: [Self; 5] = [
        Self::Cyan,
        Self::Emerald,
        Self::Crimson,
        Self::Violet,
        Self::Amber,
    ];

    pub const fn label(self) -> &'static str {
        match self {
            Self::Cyan => "Cyan",
            Self::Emerald => "Emerald",
            Self::Crimson => "Crimson",
            Self::Violet => "Violet",
            Self::Amber => "Amber",
        }
    }

    pub const fn key(self) -> &'static str {
        match self {
            Self::Cyan => "cyan",
            Self::Emerald => "emerald",
            Self::Crimson => "crimson",
            Self::Violet => "violet",
            Self::Amber => "amber",
        }
    }

    pub const fn primary_hex(self) -> &'static str {
        match self {
            Self::Cyan => "#38bdf8",
            Self::Emerald => "#34d399",
            Self::Crimson => "#fb7185",
            Self::Violet => "#a78bfa",
            Self::Amber => "#fbbf24",
        }
    }

    pub const fn secondary_hex(self) -> &'static str {
        match self {
            Self::Cyan => "#0284c7",
            Self::Emerald => "#059669",
            Self::Crimson => "#e11d48",
            Self::Violet => "#7c3aed",
            Self::Amber => "#d97706",
        }
    }

    pub const fn primary(self) -> u32 {
        match self {
            Self::Cyan => 0x38bdf8,
            Self::Emerald => 0x34d399,
            Self::Crimson => 0xfb7185,
            Self::Violet => 0xa78bfa,
            Self::Amber => 0xfbbf24,
        }
    }

    pub const fn secondary(self) -> u32 {
        match self {
            Self::Cyan => 0x0284c7,
            Self::Emerald => 0x059669,
            Self::Crimson => 0xe11d48,
            Self::Violet => 0x7c3aed,
            Self::Amber => 0xd97706,
        }
    }

    pub const fn glow_rgba(self) -> (u8, u8, u8, f32) {
        match self {
            Self::Cyan => (56, 189, 248, 0.35),
            Self::Emerald => (52, 211, 153, 0.35),
            Self::Crimson => (251, 113, 133, 0.35),
            Self::Violet => (167, 139, 250, 0.35),
            Self::Amber => (251, 191, 36, 0.35),
        }
    }

    pub const fn soft_rgba(self) -> (u8, u8, u8, f32) {
        match self {
            Self::Cyan => (56, 189, 248, 0.15),
            Self::Emerald => (52, 211, 153, 0.15),
            Self::Crimson => (251, 113, 133, 0.15),
            Self::Violet => (167, 139, 250, 0.15),
            Self::Amber => (251, 191, 36, 0.15),
        }
    }

    pub const fn play_gradient(self) -> (u32, u32, u32) {
        match self {
            Self::Cyan => (0xe0f2fe, 0xbae6fd, 0x38bdf8),
            Self::Emerald => (0xd1fae5, 0x6ee7b7, 0x10b981),
            Self::Crimson => (0xffe4e6, 0xfda4af, 0xf43f5e),
            Self::Violet => (0xede9fe, 0xc4b5fd, 0x8b5cf6),
            Self::Amber => (0xfef3c7, 0xfde68a, 0xf59e0b),
        }
    }

    pub const fn values(self) -> PaletteValues {
        let (p1, p2, p3) = self.play_gradient();
        PaletteValues {
            primary: self.primary(),
            secondary: self.secondary(),
            glow: self.glow_rgba(),
            soft: self.soft_rgba(),
            p1,
            p2,
            p3,
        }
    }

    pub const fn from_u8(val: u8) -> Self {
        match val {
            1 => Self::Emerald,
            2 => Self::Crimson,
            3 => Self::Violet,
            4 => Self::Amber,
            _ => Self::Cyan,
        }
    }
}

pub mod tokens {
    pub const BG: u32 = 0x05070b;
    pub const TEXT: u32 = 0xf1f5f9;
    pub const MUTED_SLATE: u32 = 0x94a3b8;
    pub const PLAY_TEXT: u32 = 0x020617;

    pub const GLASS_PANEL_RGB: (u8, u8, u8) = (14, 19, 28);
    pub const DEFAULT_GLASS_OPACITY: f32 = 0.72;
    pub const GLASS_BORDER_RGBA: (u8, u8, u8, f32) = (255, 255, 255, 0.09);

    pub const PILL_RGB: (u8, u8, u8) = (10, 14, 22);
    pub const PILL_OPACITY: f32 = 0.78;
    pub const PILL_BORDER_RGBA: (u8, u8, u8, f32) = (255, 255, 255, 0.08);

    pub const MIN_OPACITY: f32 = 0.30;
    pub const MAX_OPACITY: f32 = 0.95;
    pub const DEFAULT_BLUR: f32 = 16.0;
    pub const MIN_BLUR: f32 = 0.0;
    pub const MAX_BLUR: f32 = 32.0;

    pub const ROUNDED_2XL: f32 = 16.0;
    pub const ROUNDED_XL: f32 = 12.0;
    pub const ROUNDED_LG: f32 = 8.0;
    pub const ROUNDED_FULL: f32 = 999.0;

    pub const GAP_2_5: f32 = 10.0;
    pub const P_3_5: f32 = 14.0;

    pub const TEXT_9PX: f32 = 9.0;
    pub const TEXT_10PX: f32 = 10.0;
    pub const TEXT_11PX: f32 = 11.0;
    pub const TEXT_XS: f32 = 12.0;
    pub const TEXT_SM: f32 = 14.0;
    pub const TEXT_LG: f32 = 18.0;

    pub const FONT_MAIN: &str = "Inter";
    pub const FONT_MONO: &str = "JetBrains Mono";
}
