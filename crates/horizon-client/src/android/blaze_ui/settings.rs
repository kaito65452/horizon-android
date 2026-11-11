use super::*;
use crate::catalog::SettingsCategory;
use horizon_client::ui_theme::{Face, Icon, Ui, color};

pub fn draw_settings_view(
    launcher: &mut Launcher,
    ui: &mut Ui,
    content_x: f32,
    content_w: f32,
    h: f32,
    compact: bool,
    slide_y: f32,
    _ime_top_dp: Option<f32>,
) {
    let active_cat = match launcher.catalog.settings_category {
        SettingsCategory::Overview | SettingsCategory::Launcher => SettingsCategory::Appearance,
        other => other,
    };

    let top_y = 56.0 + slide_y;
    let bottom_y = h - 28.0;
    let panel_h = (bottom_y - top_y).max(120.0);

    let rail_w = if compact {
        150.0
    } else {
        (content_w * 0.26).clamp(150.0, 220.0)
    };
    let panel_x = content_x + rail_w + 12.0;
    let panel_w = (content_w - rail_w - 12.0).max(180.0);

    let theme = horizon_client::ui_theme::active_theme();
    let glass_card_col = color::with_alpha(blaze_card_bg(), theme.glass_opacity);

    // 1. Left Category Rail (with layout bounds protection)
    ui.rounded(
        content_x,
        top_y,
        rail_w,
        panel_h,
        GLASS_RADIUS,
        glass_card_col,
    );
    ui.border(
        content_x,
        top_y,
        rail_w,
        panel_h,
        GLASS_RADIUS,
        blaze_card_border(),
    );

    ui.text(
        Face::SemiBold,
        10.5,
        content_x + 16.0,
        top_y + 14.0,
        blaze_accent(),
        "SETTINGS",
    );

    let rail_items: [(SettingsCategory, Icon, &str); 8] = [
        (SettingsCategory::Appearance, Icon::Settings, "Appearance"),
        (
            SettingsCategory::EngineMemory,
            Icon::Monitor,
            "Engine & Memory",
        ),
        (
            SettingsCategory::DownloadsKeepAlive,
            Icon::Installed,
            "Downloads & Keep-alive",
        ),
        (SettingsCategory::Controls, Icon::Gamepad, "Controls"),
        (SettingsCategory::Account, Icon::MicrosoftGrid, "Account"),
        (SettingsCategory::Storage, Icon::Folder, "Storage"),
        (SettingsCategory::Experimental, Icon::Flask, "Experimental"),
        (
            SettingsCategory::About,
            Icon::Terminal,
            "About & Diagnostics",
        ),
    ];

    let item_h = 34.0;
    let item_gap = 4.0;
    let items_top = top_y + 32.0;

    // Sidebar clipping so it never draws below the panel bottom
    ui.set_clip(Some([content_x, top_y + 30.0, rail_w, panel_h - 34.0]));
    for (i, (c_enum, icon, label)) in rail_items.into_iter().enumerate() {
        let iy = items_top + i as f32 * (item_h + item_gap);
        let is_selected = active_cat == c_enum
            || (active_cat == SettingsCategory::Theme && c_enum == SettingsCategory::Appearance)
            || (active_cat == SettingsCategory::Video && c_enum == SettingsCategory::EngineMemory)
            || (active_cat == SettingsCategory::Diagnostics && c_enum == SettingsCategory::About);
        let r = [content_x + 8.0, iy, rail_w - 16.0, item_h];
        launcher.targets.push((r, Action::SettingsCat(c_enum)));

        if is_selected {
            ui.rounded(r[0], r[1], r[2], r[3], 8.0, blaze_nav_active());
            ui.border(r[0], r[1], r[2], r[3], 8.0, blaze_accent());
        }

        let icon_c = if is_selected {
            blaze_accent()
        } else {
            blaze_muted()
        };
        ui.icon(
            icon,
            r[0] + 10.0,
            r[1] + (item_h - 16.0) * 0.5,
            16.0,
            icon_c,
        );

        let text_c = if is_selected {
            blaze_text()
        } else {
            blaze_muted()
        };
        let fit_lbl = ui.fit_text(
            if is_selected {
                Face::SemiBold
            } else {
                Face::Regular
            },
            11.5,
            label,
            (rail_w - 48.0).max(40.0),
        );
        ui.text(
            if is_selected {
                Face::SemiBold
            } else {
                Face::Regular
            },
            11.5,
            r[0] + 32.0,
            r[1] + (item_h - 14.0) * 0.5,
            text_c,
            &fit_lbl,
        );
    }
    ui.set_clip(None);

    // 2. Right Content Panel
    ui.rounded(
        panel_x,
        top_y,
        panel_w,
        panel_h,
        GLASS_RADIUS,
        glass_card_col,
    );
    ui.border(
        panel_x,
        top_y,
        panel_w,
        panel_h,
        GLASS_RADIUS,
        blaze_card_border(),
    );

    let list_top = top_y + 8.0;
    let list_bottom = top_y + panel_h - 8.0;
    let row_h = 56.0;

    ui.set_clip(Some([panel_x, list_top, panel_w, list_bottom - list_top]));

    match active_cat {
        SettingsCategory::Appearance | SettingsCategory::Theme => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "THEME & SURFACE ENGINE",
                "Appearance",
            );
            cy += 44.0;

            // SwatchRow: Cyan, Green, Red, Violet, Gold
            let cur_pal = launcher.catalog.config.theme.palette_idx;
            draw_swatch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Accent color palette",
                cur_pal,
            );
            cy += row_h;

            // Background style Segmented
            let bg_style = launcher.catalog.config.theme.bg_style.as_str();
            draw_segmented(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Background style",
                Some("Visual scenery theme"),
                [
                    ("Default art", Action::SelectBgStyle("mc"), bg_style == "mc"),
                    (
                        "Animated",
                        Action::SelectBgStyle("shader"),
                        bg_style == "shader",
                    ),
                    (
                        "Custom",
                        Action::SelectBgStyle("custom"),
                        bg_style == "custom",
                    ),
                    (
                        "OLED black",
                        Action::SelectBgStyle("oled"),
                        bg_style == "oled",
                    ),
                ],
            );
            cy += row_h;

            // Import media ActionButton
            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Custom media background",
                Some("Import image or video wallpaper"),
                "Import image / video",
                Action::ChooseImportBackground,
                false,
            );
            cy += row_h;

            // Panel opacity slider (30-95%)
            let op = launcher.catalog.config.theme.glass_opacity;
            let op_pct = (op * 100.0).round() as i32;
            let op_label = format!("{op_pct}%");
            let op_frac = (op - 0.30) / (0.95 - 0.30);
            draw_slider_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Panel opacity",
                Some("Glassmorphism translucency"),
                &op_label,
                op_frac,
                Action::GlassOpacityDelta(0.0),
            );
            cy += row_h;

            // Panel blur slider (0-32px)
            let blur = launcher.catalog.config.theme.glass_blur;
            let blur_label = format!("{blur:.0} px");
            let blur_frac = blur / 32.0;
            draw_slider_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Panel blur",
                Some("Backdrop blur strength"),
                &blur_label,
                blur_frac,
                Action::GlassBlurDelta(0.0),
            );
            cy += row_h;

            // UI Scale
            let scale_label = launcher.catalog.config.ui_scale_label();
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "UI Scale",
                Some("Display DPI scaling"),
                scale_label,
                Action::UiScale,
            );
            cy += row_h;

            // Animations Switch
            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Launcher animations",
                Some("Page transitions and motion"),
                !launcher.catalog.config.disable_launcher_animations,
                Action::ToggleLauncherAnimations,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::EngineMemory | SettingsCategory::Video => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "ENGINE & MEMORY",
                "Renderer",
            );
            cy += 44.0;

            let rend = launcher.catalog.config.renderer.label();
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Graphics renderer",
                Some("Vulkan or GLES"),
                rend,
                Action::Renderer,
            );
            cy += row_h;

            let scale_str = format!("{}%", launcher.catalog.config.render_scale.percent());
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Render scale",
                Some("Internal resolution"),
                &scale_str,
                Action::RenderScale,
            );
            cy += row_h;

            let hz = launcher.catalog.config.device.display_label();
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Display refresh rate",
                Some("Screen Hz"),
                &hz,
                Action::DisplayHz,
            );
            cy += row_h;

            let asp = launcher.catalog.config.screen.aspect.label();
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Aspect ratio",
                Some("Display stretch"),
                asp,
                Action::Aspect,
            );
            cy += row_h;

            let orient = launcher.catalog.config.orientation.label();
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Orientation",
                Some("Landscape lock"),
                orient,
                Action::Orientation,
            );
            cy += row_h;

            let cache = if launcher.catalog.config.cache_budget_mib == 0 {
                "Default (80 MiB)".to_owned()
            } else {
                format!("{} MiB", launcher.catalog.config.cache_budget_mib)
            };
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Cache budget",
                Some("Disk/RAM budget"),
                &cache,
                Action::CacheBudget,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::DownloadsKeepAlive => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "NETWORK & BACKGROUND",
                "Persistence",
            );
            cy += 44.0;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Keep-alive service",
                Some("Background downloads"),
                launcher.catalog.config.keepalive_enabled,
                Action::ToggleKeepAlive,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Starling voice chat",
                Some("Proximity audio"),
                launcher.catalog.config.svc_enabled,
                Action::ToggleExperimentalSvc,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Voice noise suppression",
                Some("RNNoise AI filter"),
                launcher.catalog.config.svc_rnnoise,
                Action::ToggleExperimentalSvcRnnoise,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Fast chunk upload",
                Some("Multi-threaded meshing"),
                launcher.catalog.config.fast_chunk_upload,
                Action::ToggleFastChunkUpload,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::Controls => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "TOUCH & GAMEPAD",
                "Controls",
            );
            cy += 44.0;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Control editor",
                Some("Custom HUD layout"),
                "Open editor",
                Action::OpenControlEditor,
                true,
            );
            cy += row_h;

            let host = launcher.catalog.config.input_host.label();
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Input host mode",
                Some("Controller vs Touch"),
                host,
                Action::InputHost,
            );
            cy += row_h;

            let game_home = launcher.catalog.config.game_home(&launcher.home);
            let touch_cfg = horizon_client::touch::Settings::load_from(&game_home.join("horizon-touch.json"));

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Gyro aiming",
                Some("Motion sensor control"),
                touch_cfg.gyroscope,
                Action::ToggleGyro,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Virtual mouse",
                Some("Touch pointer emulation"),
                touch_cfg.virtual_mouse_enabled,
                Action::ToggleVirtualMouseEnabled,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Haptic vibration",
                Some("Touch feedback"),
                touch_cfg.vibrate,
                Action::ToggleTouchVibrate,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::Account => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "PROFILE & AUTH",
                "Account",
            );
            cy += 44.0;

            let prof_num = format!("Profile {}", launcher.catalog.config.profile + 1);
            draw_dropdown_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Active profile",
                Some("Configuration slot"),
                &prof_num,
                Action::Profile,
            );
            cy += row_h;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Account login",
                Some("Microsoft / Ely.by / Offline"),
                "Switch account",
                Action::Account,
                false,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::Storage => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "FILES & EXPORT",
                "Storage",
            );
            cy += 44.0;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Minecraft worlds (SAF)",
                Some("Export to Android storage"),
                "Export worlds",
                Action::WorldFiles,
                false,
            );
            cy += row_h;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Game folder (SAF)",
                Some("Browse files"),
                "Open files",
                Action::GameFiles,
                false,
            );
            cy += row_h;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Export settings",
                Some("Backup JSON"),
                "Export JSON",
                Action::ExportAllSettings,
                false,
            );
            cy += row_h;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Import settings",
                Some("Restore JSON"),
                "Import JSON",
                Action::ImportAllSettings,
                false,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::Experimental => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "LABS & FLAGS",
                "Experimental",
            );
            cy += 44.0;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Zero latency touch",
                Some("Bypasses buffer queues"),
                launcher.catalog.config.zero_latency_touch,
                Action::ToggleZeroLatencyTouch,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Super performance mode",
                Some("Low fidelity, max FPS"),
                launcher.catalog.config.super_perf_mode,
                Action::ToggleSuperPerf,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Battery saver profile",
                Some("Throttles draw loop"),
                launcher.catalog.config.battery_perf_mode,
                Action::ToggleBatteryPerf,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Hollow leaf culling",
                Some("Optimized foliage"),
                launcher.catalog.config.experimental_leaf_culling,
                Action::ToggleExperimentalLeaves,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::About | SettingsCategory::Diagnostics => {
            let mut cy = list_top - launcher.catalog.scroll;
            draw_section_header(
                ui,
                panel_x + 12.0,
                cy,
                panel_w - 24.0,
                "ABOUT & SYSTEM",
                "Information",
            );
            cy += 44.0;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Version",
                Some("Horizon Android 26.3 • Rust"),
                "Diagnostics",
                Action::SettingsCat(SettingsCategory::Diagnostics),
                false,
            );
            cy += row_h;

            // GitHub repository button moved to Settings > About
            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "GitHub repository",
                Some("Source code & releases"),
                "Open GitHub",
                Action::Repository,
                true,
            );
            cy += row_h;

            draw_switch_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Record performance",
                Some("Telemetry frame trace"),
                launcher.catalog.config.record_performance,
                Action::RecordPerformance,
            );
            cy += row_h;

            draw_action_button_row(
                ui,
                &mut launcher.targets,
                &mut launcher.list_targets,
                panel_x,
                cy,
                panel_w,
                row_h,
                "Export performance",
                Some("Save frame logs"),
                "Export JSONL",
                Action::ExportPerformance,
                false,
            );
            cy += row_h;

            let max_scroll = (cy + launcher.catalog.scroll - list_bottom).max(0.0);
            launcher.catalog.scroll = launcher.catalog.scroll.clamp(0.0, max_scroll);
            launcher.list_view = Some([list_top, list_bottom, max_scroll]);
        }
        SettingsCategory::Overview | SettingsCategory::Launcher => {}
    }

    ui.set_clip(None);
}
