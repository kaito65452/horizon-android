use super::*;
use crate::catalog::SettingsCategory;
use horizon_client::{
    gfx::Renderer,
    osk::Output,
    ui_theme::{Face, Icon, LayoutRect, Ui, color},
};
use std::sync::atomic::Ordering;
use winit::{
    event::{ActiveEventLoop, KeyEvent, Touch, TouchPhase},
    keyboard::{KeyCode, PhysicalKey},
};

pub mod browse;
pub mod components;
pub mod home;
pub mod manager;
pub mod settings;
pub mod settings_components;
pub mod shell;
pub mod skin;

pub use browse::*;
pub use components::*;
pub use home::*;
pub use manager::*;
pub use settings::*;
pub use settings_components::*;
pub use shell::*;
pub use skin::*;

thread_local! {
    pub static ACTIVE_SLIDER_DRAG: std::cell::Cell<Option<(Action, f32, f32)>> = const { std::cell::Cell::new(None) };
    pub static SLIDER_TRACK_TARGETS: std::cell::RefCell<Vec<([f32; 4], Action, f32, f32)>> = const { std::cell::RefCell::new(Vec::new()) };
    pub static WALLPAPER_CACHE: std::cell::RefCell<Option<(&'static str, u32, u32, Vec<u8>)>> = const { std::cell::RefCell::new(None) };
}

const WALLPAPER_CYAN: &[u8] = include_bytes!("wallpapers/cyan.png");
const WALLPAPER_EMERALD: &[u8] = include_bytes!("wallpapers/emerald.png");
const WALLPAPER_CRIMSON: &[u8] = include_bytes!("wallpapers/crimson.png");
const WALLPAPER_VIOLET: &[u8] = include_bytes!("wallpapers/violet.png");
const WALLPAPER_AMBER: &[u8] = include_bytes!("wallpapers/amber.png");
const WALLPAPER_WINTER: &[u8] = include_bytes!("wallpapers/winter.png");

thread_local! {
    static ACTIVE_SLIDER_DRAG: std::cell::Cell<Option<(Action, f32, f32)>> = const { std::cell::Cell::new(None) };
    static SLIDER_TRACK_TARGETS: std::cell::RefCell<Vec<([f32; 4], Action, f32, f32)>> = const { std::cell::RefCell::new(Vec::new()) };
    static WALLPAPER_CACHE: std::cell::RefCell<Option<(&'static str, u32, u32, Vec<u8>)>> = const { std::cell::RefCell::new(None) };
}

fn is_december() -> bool {
    let now = std::time::SystemTime::now();
    let Ok(dur) = now.duration_since(std::time::UNIX_EPOCH) else {
        return false;
    };
    let secs = dur.as_secs();
    let days = (secs / 86400) as i64;
    let z = days + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = (z - era * 146097) as u32;
    let yoe = (doe - doe / 1024 + doe / 1461 - doe / 142401) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    m == 12
}

fn decode_png_rgba(bytes: &[u8]) -> Option<(u32, u32, Vec<u8>)> {
    let decoder = png::Decoder::new(std::io::Cursor::new(bytes));
    let mut reader = decoder.read_info().ok()?;
    let mut buf = vec![0u8; reader.output_buffer_size()?];
    let info = reader.next_frame(&mut buf).ok()?;
    let width = info.width;
    let height = info.height;
    buf.truncate(info.buffer_size());

    let rgba = match info.color_type {
        png::ColorType::Rgba => buf,
        png::ColorType::Rgb => {
            let mut out = Vec::with_capacity((width * height * 4) as usize);
            for chunk in buf.chunks_exact(3) {
                out.extend_from_slice(&[chunk[0], chunk[1], chunk[2], 255]);
            }
            out
        }
        png::ColorType::Grayscale => {
            let mut out = Vec::with_capacity((width * height * 4) as usize);
            for &g in &buf {
                out.extend_from_slice(&[g, g, g, 255]);
            }
            out
        }
        png::ColorType::GrayscaleAlpha => {
            let mut out = Vec::with_capacity((width * height * 4) as usize);
            for chunk in buf.chunks_exact(2) {
                out.extend_from_slice(&[chunk[0], chunk[0], chunk[0], chunk[1]]);
            }
            out
        }
        _ => return None,
    };
    Some((width, height, rgba))
}

fn get_theme_wallpaper_bytes(
    palette: horizon_theme::blaze::AccentPalette,
) -> (&'static str, &'static [u8]) {
    if is_december() {
        return ("blaze_wallpaper_winter", WALLPAPER_WINTER);
    }
    match palette {
        horizon_theme::blaze::AccentPalette::Cyan => ("blaze_wallpaper_cyan", WALLPAPER_CYAN),
        horizon_theme::blaze::AccentPalette::Emerald => {
            ("blaze_wallpaper_emerald", WALLPAPER_EMERALD)
        }
        horizon_theme::blaze::AccentPalette::Crimson => {
            ("blaze_wallpaper_crimson", WALLPAPER_CRIMSON)
        }
        horizon_theme::blaze::AccentPalette::Violet => ("blaze_wallpaper_violet", WALLPAPER_VIOLET),
        horizon_theme::blaze::AccentPalette::Amber => ("blaze_wallpaper_amber", WALLPAPER_AMBER),
    }
}

fn load_active_wallpaper(
    palette: horizon_theme::blaze::AccentPalette,
) -> Option<(&'static str, u32, u32, Vec<u8>)> {
    let (key, bytes) = get_theme_wallpaper_bytes(palette);
    WALLPAPER_CACHE.with(|cache| {
        let mut borrow = cache.borrow_mut();
        if let Some((cached_key, w, h, rgba)) = borrow.as_ref() {
            if *cached_key == key {
                return Some((*cached_key, *w, *h, rgba.clone()));
            }
        }
        let (w, h, rgba) = decode_png_rgba(bytes)?;
        *borrow = Some((key, w, h, rgba.clone()));
        Some((key, w, h, rgba))
    })
}

pub(crate) struct AnimState {
    pub last_tick: std::time::Instant,
    pub last_page: Page,
    pub last_category: SettingsCategory,
    pub last_has_modal: bool,
    pub nav_pill_x: f32,
    pub nav_pill_y: f32,
    pub target_nav_x: f32,
    pub target_nav_y: f32,
    pub page_fade: f32,
    pub page_slide: f32,
    pub progress_smooth: f32,
    pub modal_anim: f32,
    pub shimmer_phase: f32,
    pub section_expand: [f32; 3],
    pub theme_pulse: f32,
    pub intro_start: Option<std::time::Instant>,
    pub intro_elapsed: f32,
    pub intro_done: bool,
}

impl Default for AnimState {
    fn default() -> Self {
        Self {
            last_tick: std::time::Instant::now(),
            last_page: Page::Play,
            last_category: SettingsCategory::Overview,
            last_has_modal: false,
            nav_pill_x: 62.0,
            nav_pill_y: 62.0,
            target_nav_x: 62.0,
            target_nav_y: 62.0,
            page_fade: 1.0,
            page_slide: 0.0,
            progress_smooth: 0.0,
            modal_anim: 0.0,
            shimmer_phase: 0.0,
            section_expand: [0.0; 3],
            theme_pulse: 1.0,
            intro_start: Some(std::time::Instant::now()),
            intro_elapsed: 0.0,
            intro_done: false,
        }
    }
}

impl AnimState {
    pub fn step(
        &mut self,
        page: Page,
        category: SettingsCategory,
        target_nav_y: f32,
        has_modal: bool,
        sections: [bool; 3],
    ) {
        let now = std::time::Instant::now();
        let intro_t0 = *self.intro_start.get_or_insert(now);
        self.intro_elapsed = now.duration_since(intro_t0).as_secs_f32();
        let raw_dt = now
            .duration_since(self.last_tick)
            .as_secs_f32()
            .clamp(0.001, 0.08);
        self.last_tick = now;
        self.target_nav_y = target_nav_y;
        self.target_nav_x = target_nav_y;

        if horizon_client::ui_theme::motion::reduced_motion() {
            self.intro_done = true;
            self.last_page = page;
            self.last_category = category;
            self.last_has_modal = has_modal;
            self.nav_pill_y = target_nav_y;
            self.nav_pill_x = target_nav_y;
            self.page_fade = 1.0;
            self.page_slide = 0.0;
            self.modal_anim = if has_modal { 1.0 } else { 0.0 };
            self.theme_pulse = 1.0;
            for i in 0..3 {
                self.section_expand[i] = if sections[i] { 1.0 } else { 0.0 };
            }
            return;
        }

        let page_changed =
            page != self.last_page || (page == Page::Settings && category != self.last_category);
        let modal_changed = has_modal != self.last_has_modal;
        self.last_has_modal = has_modal;

        if page_changed {
            self.last_page = page;
            self.last_category = category;
            self.page_fade = 0.0;
            self.page_slide = 18.0;
        }
        if modal_changed && has_modal {
            self.modal_anim = 0.0;
        }

        // Clamp first transition frame dt to 16ms so an idle gap before a tap never
        // consumes most of the entrance animation before the first frame renders.
        let dt = if page_changed || modal_changed {
            raw_dt.min(0.016)
        } else {
            raw_dt
        };

        // Frame-rate independent exponential smoothing for fluid motion
        let pill_factor = 1.0 - (-22.0 * dt).exp();
        self.nav_pill_y += (target_nav_y - self.nav_pill_y) * pill_factor;
        self.nav_pill_x = self.nav_pill_y;
        let fade_factor = 1.0 - (-14.0 * dt).exp();
        self.page_fade = (self.page_fade + fade_factor * (1.0 - self.page_fade)).min(1.0);
        self.page_slide *= (-18.0 * dt).exp();
        let modal_target = if has_modal { 1.0 } else { 0.0 };
        let modal_factor = 1.0 - (-18.0 * dt).exp();
        self.modal_anim += (modal_target - self.modal_anim) * modal_factor;
        self.theme_pulse = (self.theme_pulse + dt * 6.5).min(1.0);
        for i in 0..3 {
            let target = if sections[i] { 1.0 } else { 0.0 };
            let expand_factor = 1.0 - (-18.0 * dt).exp();
            self.section_expand[i] += (target - self.section_expand[i]) * expand_factor;
        }
        self.shimmer_phase = (self.shimmer_phase + dt * 0.95) % 1.0;
    }

    pub fn trigger_page_transition(&mut self) {
        if !horizon_client::ui_theme::motion::reduced_motion() {
            self.last_tick = std::time::Instant::now();
            self.page_fade = 0.0;
            self.page_slide = 18.0;
        }
    }

    pub fn trigger_theme_pulse(&mut self) {
        if !horizon_client::ui_theme::motion::reduced_motion() {
            self.last_tick = std::time::Instant::now();
            self.theme_pulse = 0.0;
        }
    }

    pub fn is_active(&self) -> bool {
        if !self.intro_done {
            return true;
        }
        if horizon_client::ui_theme::motion::reduced_motion() {
            return false;
        }
        self.page_fade < 0.99
            || self.page_slide.abs() > 0.35
            || (self.nav_pill_y - self.target_nav_y).abs() > 0.5
            || (self.modal_anim > 0.01 && self.modal_anim < 0.99)
            || self.theme_pulse < 0.99
            || self.section_expand.iter().any(|v| *v > 0.01 && *v < 0.99)
    }
}

pub(super) enum Action {
    Page(Page),
    SettingsCat(SettingsCategory),
    Play,
    PauseInstall,
    CancelInstall,
    PauseCatalogDownload,
    CancelCatalogDownload,
    Search,
    ShaderSource(crate::shader_catalog::Origin),
    ModSource(crate::shader_catalog::Origin),
    Field(&'static str),
    FinishNameEdit,
    Install(usize),
    OpenProject(usize),
    OpenBundledProject(&'static str),
    CloseProjectModal,
    SelectModalVersion(usize),
    InstallModalProject,
    Import(horizon_client::content_files::Kind),
    OpenFolder(horizon_client::content_files::Kind),
    ToggleSection(usize),
    ToggleLocal(usize),
    DeleteLocal(usize),
    ConfirmDeleteLocal,
    ConfirmExit,
    Next,
    Previous,
    Profile,
    Logs,
    RecordPerformance,
    ExportPerformance,
    ExportDiagnostics,
    Account,
    AddAccount,
    SignIn,
    ElySignIn,
    MicrosoftAccount,
    OfflineAccount,
    SubmitEly,
    SavedAccounts,
    SavedPage(bool),
    SelectAccount(usize),
    SelectOffline,
    WardrobeKind(bool),
    WardrobeSelect(usize),
    WardrobeImport,
    WardrobeCloud,
    WardrobeUpload,
    WardrobeActivateCape(usize),
    WardrobeUseCloud,
    WardrobeModel,
    WardrobeRotate(i8),
    WardrobeZoom(bool),
    WardrobeHideCape,
    DeviceSignIn,
    CopyLoginCode,
    CancelLogin,
    ReviewApp,
    Repository,
    SignOut,
    LoginLink,
    CloseLogin,
    Renderer,
    RenderScale,
    ChooseRenderScale(crate::display::RenderScale),
    UiScale,
    ChooseUiScale(u32),
    Orientation,
    InputHost,
    ChooseInputHost(crate::catalog::InputHost),
    ChooseOrientation(crate::catalog::Orientation),
    ChooseRenderer(crate::catalog::RendererMode),
    ChooseProfile(u32),
    Dismiss,
    Immersive,
    Cutout,
    Aspect,
    ChooseAspect(crate::display::Aspect),
    DisplayHz,
    ChooseDisplayHz(u16),
    CacheBudget,
    ChooseCacheBudget(u32),
    ToggleBundled(&'static str),
    WorldFiles,
    GameFiles,
    StarlingDownload,
    WorkerLimit,
    ChooseWorkerLimit(u32),
    PerformanceHints,
    GpuProfile,
    // Video settings actions (options.txt)
    RenderScaleDelta(i32),
    RenderDistDelta(i32),
    SimDistDelta(i32),
    CycleMaxFps,
    CycleGraphicsPreset,
    ToggleVsync,
    ToggleSmoothLighting,
    CycleClouds,
    ToggleMenuBlur,
    FovDelta(f32),
    // Animation & Diagnostics Modal actions
    ToggleLauncherAnimations,
    ToggleGameAnimations,
    CloseDiagnosticsSummary,
    OpenReportsFolder,
    // Experimental settings actions
    ToggleSuperPerf,
    ToggleBatteryPerf,
    ToggleMaxFpsMode,
    ToggleZeroLatencyTouch,
    ToggleFastChunkUpload,
    ToggleExperimentalLeaves,
    ToggleExperimentalSvc,
    ToggleExperimentalSvcRnnoise,
    // Controls & Control Editor actions
    OpenControlEditor,
    CloseControlEditor,
    ControlProfileChooser,
    ChooseControlProfile(u32),
    SaveControlProfileAsNew,
    ExportControlProfile,
    ImportControlProfile,
    MouseSensDelta(f32),
    TouchSensDelta(f32),
    VirtualMouseSpeedDelta(f32),
    ButtonScaleDelta(f32),
    ToggleControlScheme,
    SelectControlMode(horizon_client::touch::ControlStyle),
    CopyActivePreset,
    EditActivePresetMeta,
    ToggleSplitControls,
    ToggleSprintLatch,
    ToggleLeftHanded,
    ToggleInvertY,
    ToggleGyro,
    GyroSensDelta(f32),
    ToggleVirtualMouseEnabled,
    PointerSizeDelta(f32),
    CycleJoystickZone,
    ToggleTouchVibrate,
    ToggleTouchUiVibrate,
    ToggleControlHub,
    ToggleAutoJump,
    ToggleInventoryControls,
    // Theme customization actions (Settings -> Launcher Settings -> Theme)
    SelectThemePreset(horizon_client::ui_theme::ThemePreset),
    SetThemeAppearance(horizon_client::ui_theme::ThemeAppearance),
    SetThemeBackgroundMode(horizon_client::ui_theme::BackgroundMode),
    SetGradientDirection(horizon_client::ui_theme::GradientDirection),
    ToggleGradientThreeStops,
    StepGradientMidPos(i8),
    SelectAccentSwatch(u32),
    SelectBgSwatch(u32),
    SelectGradStartSwatch(u32),
    SelectGradEndSwatch(u32),
    ResetTheme,
    // Blaze reference actions
    SelectAccentPalette(u8),
    SelectBgStyle(&'static str),
    GlassOpacityDelta(f32),
    GlassBlurDelta(f32),
    ToggleKeepAlive,
    ResetCategory(SettingsCategory),
    ExportAllSettings,
    ImportAllSettings,
    ClearSettingsSearch,
    ChooseImportBackground,
    DismissStarlingNotice,
    // Intro & Installed / File Manager actions
    SkipIntro,
    SetInstalledSubTab(crate::catalog::InstalledSubTab),
    SetInstalledFilter(crate::catalog::InstalledFilter),
    CycleInstalledSort,
    ExportLocal(usize),
    FmNavigateRoot(&'static str),
    FmBreadcrumb(usize),
    FmBack,
    FmForward,
    FmUp,
    FmOpenEntry(usize),
    FmToggleSelect(usize),
    FmSelectAll,
    FmClearSelection,
    FmCopy,
    FmCut,
    FmPaste,
    FmImportHere,
    FmOpenNewFolder,
    FmSubmitNewFolder,
    FmOpenRename(usize),
    FmOpenRenameSelected,
    FmSubmitRename,
    FmRequestDeleteSingle(usize),
    FmRequestDeleteSelected,
    FmConfirmDelete,
    FmOpenDetails(usize),
    FmExportSingle(usize),
    FmExportSelected,
    FmCloseModal,
}
