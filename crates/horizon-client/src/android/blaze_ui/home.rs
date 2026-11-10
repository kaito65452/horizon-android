use super::*;
use horizon_client::ui_theme::{Face, Icon, LayoutRect, Ui, color};

pub fn draw_home_view(
    launcher: &mut Launcher,
    ui: &mut Ui,
    content_x: f32,
    content_w: f32,
    h: f32,
    slide_y: f32,
) {
    use horizon_client::official_install::InstallStage;
    let installed = horizon_client::official_install::ready(&launcher.home);
    let installing = launcher.worker.is_some();
    let progress = *launcher
        .install
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);

    if installing {
        let current = InstallStage::ALL
            .iter()
            .position(|s| *s == progress.stage)
            .unwrap_or(0);
        let fraction = if progress.total > 0 {
            progress.done as f32 / progress.total as f32
        } else {
            0.0
        };
        let overall = ((current as f32 + fraction.clamp(0.0, 1.0)) / 6.0).clamp(0.02, 1.0);
        launcher.anim.progress_smooth += (overall - launcher.anim.progress_smooth) * 0.25;
        let smooth_overall = launcher.anim.progress_smooth.clamp(0.02, 1.0);
        let (done_mib, total_mib) =
            if progress.stage == InstallStage::Download && progress.total > 0 {
                (
                    progress.done as f64 / 1_048_576.0,
                    progress.total as f64 / 1_048_576.0,
                )
            } else {
                (55.3 * f64::from(overall), 55.3)
            };
        let paused = launcher.install_paused.load(Ordering::Relaxed);
        launcher.install_transfer.observe(done_mib, paused);
        let sub_left = format!(
            "Minecraft 26.3 • Profile {}",
            launcher.catalog.config.profile + 1
        );
        let sub_right = format!(
            "{done_mib:.1} / {total_mib:.1} MiB ({:.0}%)",
            overall * 100.0
        );
        let stage_text = match progress.stage {
            InstallStage::Download => "Downloading Java Edition 26.3 client.jar",
            other => other.label(),
        };
        draw_blaze_progress_view(
            ui,
            &mut launcher.targets,
            content_x,
            content_w,
            h,
            "Getting your game ready",
            &sub_left,
            &sub_right,
            &launcher.install_transfer.speed_label(),
            &launcher.install_transfer.eta_label(done_mib, total_mib),
            current,
            &[
                "Manifest",
                "Metadata",
                "Client JAR",
                "Verify",
                "Extract",
                "Ready",
            ],
            smooth_overall,
            launcher.anim.shimmer_phase,
            stage_text,
            paused,
            Action::PauseInstall,
            Action::CancelInstall,
        );
        return;
    }

    let content_top = 58.0;
    let content_h = (h - content_top - 12.0).max(240.0);
    let gap = 10.0;
    let home_rect = LayoutRect::new(content_x, content_top + slide_y, content_w, content_h);
    // 12-row proportional grid: Hero 5/12 (~40%), Action deck 3.5/12, Shortcuts 3.5/12
    let [row1, row2, row3] = home_rect.split_rows([5.0, 3.5, 3.5], gap);
    let accent = blaze_accent();
    let ink_dark = color::hex(0x020617, 255);

    // ========================================================
    // ROW 1 — Hero: clean widescreen aspect ratio
    // ========================================================
    let palette =
        horizon_theme::blaze::AccentPalette::from_u8(launcher.catalog.config.theme.palette_idx);
    let hero_drawn = match load_active_wallpaper(palette) {
        Some((key, img_w, img_h, rgba)) => ui.wallpaper(
            key,
            img_w,
            img_h,
            &rgba,
            row1.x,
            row1.y,
            row1.w,
            row1.h,
            GLASS_RADIUS,
        ),
        None => false,
    };
    if !hero_drawn {
        ui.rounded(
            row1.x,
            row1.y,
            row1.w,
            row1.h,
            GLASS_RADIUS,
            blaze_card_bg(),
        );
    }

    // Clean single-draw gradient overlay (no scanlines or banded strip artifacts)
    ui.fill_gradient(
        row1.x,
        row1.y + row1.h * 0.30,
        row1.w,
        row1.h * 0.70,
        horizon_client::ui_theme::GradientDirection::Vertical,
        color::hex(0x070A10, 0),
        color::hex(0x070A10, 235),
        Some((0.5, color::hex(0x070A10, 90))),
        16,
    );
    ui.border(
        row1.x,
        row1.y,
        row1.w,
        row1.h,
        GLASS_RADIUS,
        color::hex(0xFFFFFF, 26),
    );

    // Engine Badge
    let engine_badge = format!(
        "26.3 • Blaze Engine • {}",
        launcher.catalog.config.renderer.label()
    );
    let pill_h = 24.0;
    let badge_w = (ui.measure(Face::SemiBold, 10.5, &engine_badge) + 34.0).min(row1.w * 0.5);
    let badge_x = row1.x + row1.w - badge_w - 14.0;
    let badge_y = row1.y + 12.0;
    ui.rounded(
        badge_x,
        badge_y,
        badge_w,
        pill_h,
        pill_h * 0.5,
        color::hex(0x0A0E16, 199),
    );
    ui.border(
        badge_x,
        badge_y,
        badge_w,
        pill_h,
        pill_h * 0.5,
        color::hex(0xFFFFFF, 20),
    );
    ui.centered_fit(
        Face::SemiBold,
        10.5,
        [badge_x + 6.0, badge_y, badge_w - 22.0, pill_h],
        color::hex(0xFFFFFF, 255),
        &engine_badge,
    );
    ui.icon(
        Icon::ChevronDown,
        badge_x + badge_w - 18.0,
        badge_y + (pill_h - 11.0) * 0.5,
        11.0,
        color::hex(0x94A3B8, 255),
    );
    launcher.targets.push((
        [badge_x, badge_y, badge_w, pill_h],
        Action::Page(Page::Settings),
    ));

    // Title Block
    let title_size = (row1.h * 0.24).clamp(18.0, 30.0);
    let tag_size = 9.5;
    let tag_line = (tag_size * 1.45_f32).round();
    let title_line = (title_size * 1.45_f32).round();
    let title_x = row1.x + 20.0;
    let tag_y = row1.y + row1.h - 14.0 - tag_line;
    let title_y = tag_y - title_line + 4.0;
    ui.text(
        Face::SemiBold,
        title_size,
        title_x,
        title_y,
        color::hex(0xFFFFFF, 255),
        "MINECRAFT",
    );
    ui.text_clipped(
        Face::SemiBold,
        tag_size,
        title_x,
        tag_y,
        (row1.w * 0.5).max(40.0),
        color::hex(0xCBD5E1, 255),
        if is_december() {
            "WINTER CELEBRATION • PLAY WITHOUT LIMITS"
        } else {
            "PLAY WITHOUT LIMITS"
        },
    );

    // Capabilities Pills
    let cap_pills = [
        ("FAST", accent),
        ("STABLE", color::hex(0x34D399, 255)),
        ("CUSTOMIZABLE", color::hex(0xFBBF24, 255)),
    ];
    let cap_h = 20.0;
    let cap_y = row1.y + row1.h - 12.0 - cap_h;
    let mut pill_right = row1.x + row1.w - 14.0;
    for (cap_label, cap_col) in cap_pills.into_iter().rev() {
        let pw = ui.measure(Face::SemiBold, 9.0, cap_label) + 26.0;
        let px = pill_right - pw;
        ui.rounded(px, cap_y, pw, cap_h, cap_h * 0.5, color::hex(0x0A0E16, 199));
        ui.border(px, cap_y, pw, cap_h, cap_h * 0.5, color::hex(0xFFFFFF, 20));
        ui.rounded(px + 9.0, cap_y + cap_h * 0.5 - 2.0, 4.0, 4.0, 2.0, cap_col);
        ui.centered_fit(
            Face::SemiBold,
            9.0,
            [px + 12.0, cap_y, pw - 12.0, cap_h],
            color::hex(0xE2E8F0, 255),
            cap_label,
        );
        pill_right = px - 6.0;
    }

    // ========================================================
    // ROW 2 — Action deck (Profile / Edit / Play)
    // ========================================================
    let [prof_col, edit_col, play_col] = row2.split_cols([5.0, 2.0, 5.0], gap);
    let tile = (prof_col.h * 0.46).clamp(30.0, 38.0);

    // Profile Card
    glass_card(ui, prof_col, GLASS_RADIUS);
    let tile_y = prof_col.y + (prof_col.h - tile) * 0.5;
    let emerald = color::hex(0x34D399, 255);
    ui.rounded(
        prof_col.x + 14.0,
        tile_y,
        tile,
        tile,
        TILE_RADIUS,
        color::with_alpha(emerald, 0.20),
    );
    ui.border(
        prof_col.x + 14.0,
        tile_y,
        tile,
        tile,
        TILE_RADIUS,
        color::with_alpha(emerald, 0.40),
    );
    ui.icon(
        Icon::Gamepad,
        prof_col.x + 14.0 + (tile - 18.0) * 0.5,
        tile_y + (tile - 18.0) * 0.5,
        18.0,
        emerald,
    );
    let chev = 26.0;
    let chev_x = prof_col.x + prof_col.w - chev - 14.0;
    let text_x = prof_col.x + 14.0 + tile + 12.0;
    let text_w = (chev_x - text_x - 8.0).max(24.0);
    let show_meta = prof_col.h >= 62.0;
    let block_h = if show_meta { 46.0 } else { 32.0 };
    let mut ty = prof_col.y + (prof_col.h - block_h) * 0.5;
    ui.text_clipped(
        Face::Medium,
        8.5,
        text_x,
        ty,
        text_w,
        blaze_muted(),
        "SELECTED PROFILE",
    );
    ty += 12.0;
    let prof_name = format!("Profile {}", launcher.catalog.config.profile + 1);
    ui.text_clipped(
        Face::SemiBold,
        14.0,
        text_x,
        ty,
        text_w,
        blaze_text(),
        &prof_name,
    );
    if show_meta {
        ui.text_clipped(
            Face::Regular,
            9.5,
            text_x,
            ty + 20.0,
            text_w,
            blaze_muted(),
            "26.3 • Blaze Engine",
        );
    }
    ui.rounded(
        chev_x,
        prof_col.y + (prof_col.h - chev) * 0.5,
        chev,
        chev,
        chev * 0.5,
        tile_fill(),
    );
    ui.icon(
        Icon::ChevronDown,
        chev_x + (chev - 12.0) * 0.5,
        prof_col.y + (prof_col.h - 12.0) * 0.5,
        12.0,
        blaze_text(),
    );
    launcher
        .targets
        .push((prof_col.to_array(), Action::Profile));

    // Edit Button
    glass_card(ui, edit_col, GLASS_RADIUS);
    let edit_label_w = ui.measure(Face::SemiBold, 12.0, "EDIT");
    let edit_total = 14.0 + 6.0 + edit_label_w;
    let edit_x = edit_col.x + (edit_col.w - edit_total) * 0.5;
    ui.icon(
        Icon::Pencil,
        edit_x,
        edit_col.y + (edit_col.h - 14.0) * 0.5,
        14.0,
        accent,
    );
    ui.text(
        Face::SemiBold,
        12.0,
        edit_x + 20.0,
        edit_col.y + (edit_col.h - (12.0_f32 * 1.45).round()) * 0.5,
        blaze_text(),
        "EDIT",
    );
    launcher
        .targets
        .push((edit_col.to_array(), Action::Page(Page::Skins)));

    // Play / Launch Button
    let is_launching = launcher.launching || launcher.session_worker.is_some();
    if is_launching && installed {
        ui.rounded(
            play_col.x,
            play_col.y,
            play_col.w,
            play_col.h,
            GLASS_RADIUS,
            blaze_accent_soft(),
        );
        ui.border(
            play_col.x,
            play_col.y,
            play_col.w,
            play_col.h,
            GLASS_RADIUS,
            accent,
        );
        let bar_w = (play_col.w - 40.0).max(40.0);
        let bar_x = play_col.x + 20.0;
        let bar_y = play_col.y + play_col.h - 14.0;
        ui.rounded(bar_x, bar_y, bar_w, 3.0, 1.5, blaze_track());
        let sw = (bar_w * 0.36).clamp(24.0, bar_w);
        let sx = bar_x + (bar_w - sw) * launcher.anim.shimmer_phase.clamp(0.0, 1.0);
        ui.rounded(sx, bar_y, sw, 3.0, 1.5, accent);
        ui.centered_fit(
            Face::SemiBold,
            15.0,
            [
                play_col.x + 12.0,
                play_col.y + 4.0,
                play_col.w - 24.0,
                play_col.h - 18.0,
            ],
            blaze_text(),
            "Launching...",
        );
    } else {
        use horizon_client::ui_theme::lerp_color;
        let white = color::hex(0xFFFFFF, 255);
        let g0 = lerp_color(accent, white, 0.86);
        let g1 = lerp_color(accent, white, 0.58);
        ui.rounded(
            play_col.x,
            play_col.y,
            play_col.w,
            play_col.h,
            GLASS_RADIUS,
            g1,
        );
        ui.fill_gradient(
            play_col.x,
            play_col.y,
            play_col.w,
            play_col.h,
            horizon_client::ui_theme::GradientDirection::Horizontal,
            g0,
            accent,
            Some((0.5, g1)),
            16,
        );
        ui.border(
            play_col.x,
            play_col.y,
            play_col.w,
            play_col.h,
            GLASS_RADIUS,
            color::hex(0xFFFFFF, 60),
        );

        let icon_tile = (play_col.h * 0.42).clamp(28.0, 34.0);
        let tile_y = play_col.y + (play_col.h - icon_tile) * 0.5;
        ui.rounded(
            play_col.x + 14.0,
            tile_y,
            icon_tile,
            icon_tile,
            TILE_RADIUS,
            color::hex(0x020617, 26),
        );
        ui.icon(
            Icon::Play,
            play_col.x + 14.0 + (icon_tile - 16.0) * 0.5 + 1.0,
            tile_y + (icon_tile - 16.0) * 0.5,
            16.0,
            ink_dark,
        );
        let play_title = if installed {
            "PLAY"
        } else if launcher.error.is_some() {
            "RETRY"
        } else {
            "DOWNLOAD"
        };
        let disc = 26.0;
        let mid_x = play_col.x + 14.0 + icon_tile + 8.0;
        let mid_w = (play_col.w - (14.0 + icon_tile + 8.0) - (disc + 14.0 + 8.0)).max(40.0);
        let title_sz = 18.0;
        let sub_sz = 9.5;
        let stack_h = (title_sz * 1.2_f32).round() + (sub_sz * 1.45_f32).round();
        let stack_y = play_col.y + (play_col.h - stack_h) * 0.5;
        ui.centered_fit(
            Face::SemiBold,
            title_sz,
            [mid_x, stack_y, mid_w, (title_sz * 1.2_f32).round()],
            ink_dark,
            play_title,
        );
        ui.centered_fit(
            Face::SemiBold,
            sub_sz,
            [
                mid_x,
                stack_y + (title_sz * 1.2_f32).round(),
                mid_w,
                (sub_sz * 1.45_f32).round(),
            ],
            color::hex(0x1E293B, 255),
            "26.3 • Blaze Engine",
        );
        let disc_x = play_col.x + play_col.w - disc - 14.0;
        ui.rounded(
            disc_x,
            play_col.y + (play_col.h - disc) * 0.5,
            disc,
            disc,
            disc * 0.5,
            color::hex(0x020617, 26),
        );
        ui.icon(
            Icon::ChevronDown,
            disc_x + (disc - 14.0) * 0.5,
            play_col.y + (play_col.h - 14.0) * 0.5,
            14.0,
            ink_dark,
        );
        launcher.targets.push((play_col.to_array(), Action::Play));
    }

    // ========================================================
    // ROW 3 — Shortcut Cards
    // ========================================================
    let [browse_card, manager_card] = row3.split_cols([6.0, 6.0], gap);
    let shortcuts = [
        (
            browse_card,
            Icon::Mods,
            accent,
            "BROWSER RESOURCES",
            "Mods • Resource Packs • Shader Packs",
            (color::hex(0x6366F1, 26), color::hex(0x0EA5E9, 51)),
            Action::Page(Page::Mods),
        ),
        (
            manager_card,
            Icon::Folder,
            color::hex(0xFBBF24, 255),
            "MANAGER",
            "Installed mods, packs, and worlds",
            (color::hex(0x10B981, 26), color::hex(0xF59E0B, 51)),
            Action::Page(Page::Installed),
        ),
    ];
    for (card, icon, icon_col, title, sub, (wash_mid, wash_end), action) in shortcuts {
        glass_card(ui, card, GLASS_RADIUS);
        // Clean gradient without streaks or strip bands
        ui.fill_gradient(
            card.x + card.w * 0.60,
            card.y,
            card.w * 0.40,
            card.h,
            horizon_client::ui_theme::GradientDirection::Horizontal,
            wash_mid,
            wash_end,
            None,
            16,
        );
        ui.border(
            card.x,
            card.y,
            card.w,
            card.h,
            GLASS_RADIUS,
            glass_edge(false),
        );

        let c_tile = (card.h * 0.46).clamp(30.0, 38.0);
        let c_tile_y = card.y + (card.h - c_tile) * 0.5;
        ui.rounded(
            card.x + 16.0,
            c_tile_y,
            c_tile,
            c_tile,
            TILE_RADIUS,
            tile_fill(),
        );
        ui.border(
            card.x + 16.0,
            c_tile_y,
            c_tile,
            c_tile,
            TILE_RADIUS,
            glass_edge(false),
        );
        ui.icon(
            icon,
            card.x + 16.0 + (c_tile - 18.0) * 0.5,
            c_tile_y + (c_tile - 18.0) * 0.5,
            18.0,
            icon_col,
        );

        let arrow = 26.0;
        let arrow_x = card.x + card.w - arrow - 16.0;
        let t_x = card.x + 16.0 + c_tile + 12.0;
        let t_w = (arrow_x - t_x - 8.0).max(24.0);
        let stack = 12.0 + 14.0;
        let t_y = card.y + (card.h - stack) * 0.5 - 1.0;
        ui.text_clipped(Face::SemiBold, 11.5, t_x, t_y, t_w, blaze_text(), title);
        ui.text_clipped(Face::Regular, 9.5, t_x, t_y + 15.0, t_w, blaze_muted(), sub);
        ui.rounded(
            arrow_x,
            card.y + (card.h - arrow) * 0.5,
            arrow,
            arrow,
            arrow * 0.5,
            tile_fill(),
        );
        ui.icon(
            Icon::ArrowRight,
            arrow_x + (arrow - 12.0) * 0.5,
            card.y + (card.h - 12.0) * 0.5,
            12.0,
            blaze_text(),
        );
        launcher.targets.push((card.to_array(), action));
    }
}
