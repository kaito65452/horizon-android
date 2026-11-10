//! Blaze Launcher design tokens from `docs/blaze-ui-reference-en.html`.
//! Landscape-first layout, glassmorphic panels, and 5 curated accent palettes.

/// The 5 Blaze Accent Palettes:
/// 0: Masterix Cyan (#38bdf8)
/// 1: Blaze Gold (#f59e0b)
/// 2: Creeper Green (#10b981)
/// 3: Nether Crimson (#f43f5e)
/// 4: Ender Violet (#a855f7)
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum AccentPalette {
    #[default]
    MasterixCyan = 0,
    BlazeGold = 1,
    CreeperGreen = 2,
    NetherCrimson = 3,
    EnderViolet = 4,
}

impl AccentPalette {
    pub const ALL: [Self; 5] = [
        Self::MasterixCyan,
        Self::BlazeGold,
        Self::CreeperGreen,
        Self::NetherCrimson,
        Self::EnderViolet,
    ];

    pub const fn label(self) -> &'static str {
        match self {
            Self::MasterixCyan => "Masterix Cyan",
            Self::BlazeGold => "Blaze Gold",
            Self::CreeperGreen => "Creeper Green",
            Self::NetherCrimson => "Nether Crimson",
            Self::EnderViolet => "Ender Violet",
        }
    }

    pub const fn primary_hex(self) -> &'static str {
        match self {
            Self::MasterixCyan => "#38bdf8",
            Self::BlazeGold => "#f59e0b",
            Self::CreeperGreen => "#10b981",
            Self::NetherCrimson => "#f43f5e",
            Self::EnderViolet => "#a855f7",
        }
    }

    pub const fn primary(self) -> u32 {
        match self {
            Self::MasterixCyan => 0x38bdf8,
            Self::BlazeGold => 0xf59e0b,
            Self::CreeperGreen => 0x10b981,
            Self::NetherCrimson => 0xf43f5e,
            Self::EnderViolet => 0xa855f7,
        }
    }

    pub const fn secondary(self) -> u32 {
        match self {
            Self::MasterixCyan => 0x0284c7,
            Self::BlazeGold => 0xd97706,
            Self::CreeperGreen => 0x059669,
            Self::NetherCrimson => 0xe11d48,
            Self::EnderViolet => 0x9333ea,
        }
    }

    pub const fn from_u8(val: u8) -> Self {
        match val {
            1 => Self::BlazeGold,
            2 => Self::CreeperGreen,
            3 => Self::NetherCrimson,
            4 => Self::EnderViolet,
            _ => Self::MasterixCyan,
        }
    }
}

/// Surface & layout design tokens for Blaze landscape UI.
pub mod layout {
    pub const DEFAULT_PANEL_OPACITY: f32 = 0.85;
    pub const MIN_PANEL_OPACITY: f32 = 0.40;
    pub const MAX_PANEL_OPACITY: f32 = 0.98;

    pub const DEFAULT_BLUR_RADIUS: f32 = 16.0;
    pub const MIN_BLUR_RADIUS: f32 = 0.0;
    pub const MAX_BLUR_RADIUS: f32 = 32.0;

    pub const RADIUS_CARD: f32 = 20.0;
    pub const RADIUS_PANEL: f32 = 16.0;
    pub const RADIUS_PILL: f32 = 999.0;
    pub const RADIUS_BUTTON: f32 = 12.0;
    pub const RADIUS_SMALL: f32 = 8.0;

    pub const TOP_BAR_HEIGHT: f32 = 48.0;
    pub const SCREEN_MARGIN_X: f32 = 16.0;
    pub const SCREEN_MARGIN_Y: f32 = 12.0;
    pub const GUTTER: f32 = 12.0;
}
