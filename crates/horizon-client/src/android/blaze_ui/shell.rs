use super::*;
use horizon_client::ui_theme::{Face, Icon, Ui, color};

#[allow(clippy::too_many_arguments)]
pub(crate) fn draw_blaze_progress_view(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    content_x: f32,
    content_w: f32,
    h: f32,
    title: &str,
    subtitle_left: &str,
    subtitle_right: &str,
    speed_label: &str,
    eta_label: &str,
    step_index: usize,
    steps: &[&str],
    fraction: f32,
    shimmer_phase: f32,
    stage_text: &str,
    paused: bool,
    pause_action: Action,
    cancel_action: Action,
) {
    let center_y = (h * 0.44).clamp(168.0, h - 135.0);
    let title_fitted = ui.fit_text(Face::SemiBold, 23.0, title, content_w);
    ui.text(
        Face::SemiBold,
        23.0,
        content_x,
        center_y - 68.0,
        blaze_text(),
        &title_fitted,
    );

    // Step indicator pill badge next to subtitle
    let step_badge = format!(
        "Step {} of {}",
        (step_index + 1).min(steps.len()),
        steps.len().max(1)
    );
    let badge_w = (ui.measure(Face::SemiBold, 12.0, &step_badge) + 18.0).max(76.0);
    ui.rounded(
        content_x,
        center_y - 34.0,
        badge_w,
        22.0,
        11.0,
        blaze_accent_soft(),
    );
    ui.text(
        Face::SemiBold,
        12.0,
        content_x + 9.0,
        center_y - 31.0,
        blaze_accent(),
        &step_badge,
    );

    let right_w = ui.measure(Face::Medium, 13.5, subtitle_right);
    let left_x = content_x + badge_w + 10.0;
    let left_max = (content_w - badge_w - right_w - 24.0).max(60.0);
    let left_fitted = ui.fit_text(Face::Regular, 13.5, subtitle_left, left_max);
    ui.text(
        Face::Regular,
        13.5,
        left_x,
        center_y - 32.0,
        blaze_muted(),
        &left_fitted,
    );
    ui.text(
        Face::Medium,
        13.5,
        content_x + content_w - right_w,
        center_y - 32.0,
        blaze_text(),
        subtitle_right,
    );

    // Segmented Blaze rounded progress bar with animated shimmer.
    let bar_y = center_y + 2.0;
    let bar_h = 12.0;
    ui.rounded(content_x, bar_y, content_w, bar_h, 6.0, blaze_track());
    let clamped = fraction.clamp(0.0, 1.0);
    if clamped > 0.0 {
        let fill_w = (content_w * clamped).max(bar_h).min(content_w);
        ui.rounded(content_x, bar_y, fill_w, bar_h, 6.0, blaze_accent());
        if !paused && fill_w > 36.0 {
            let sw = (fill_w * 0.25).clamp(24.0, 72.0);
            let sx = content_x + (fill_w - sw) * shimmer_phase.clamp(0.0, 1.0);
            ui.rounded(
                sx,
                bar_y + 2.0,
                sw,
                bar_h - 4.0,
                4.0,
                color::hex(0xC9B8FF, 165),
            );
        }
    }

    // Stage description on left + Download Speed & ETA on right
    let metrics_line = format!("{speed_label}  •  {eta_label}");
    let metrics_w = ui.measure(Face::Medium, 13.0, &metrics_line);
    let stage_line = if paused {
        format!("Paused • {stage_text}")
    } else {
        stage_text.to_owned()
    };
    let stage_max = (content_w - metrics_w - 16.0).max(80.0);
    let stage_fitted = ui.fit_text(Face::Regular, 13.0, &stage_line, stage_max);
    ui.text(
        Face::Regular,
        13.0,
        content_x,
        bar_y + 20.0,
        blaze_muted(),
        &stage_fitted,
    );
    ui.text(
        Face::Medium,
        13.0,
        content_x + content_w - metrics_w,
        bar_y + 20.0,
        blaze_accent(),
        &metrics_line,
    );

    // Multi-step visual progress pills row
    if !steps.is_empty() {
        let step_y = bar_y + 48.0;
        let gap = 6.0;
        let n = steps.len() as f32;
        let pill_w = ((content_w - gap * (n - 1.0)) / n).max(36.0);
        for (idx, name) in steps.iter().enumerate() {
            let px = content_x + idx as f32 * (pill_w + gap);
            let done = idx < step_index;
            let active = idx == step_index;
            let bg = if active {
                blaze_accent_soft()
            } else if done {
                blaze_surface_high()
            } else {
                blaze_surface()
            };
            let border_col = if active || done {
                blaze_accent()
            } else {
                blaze_border()
            };
            ui.rounded(px, step_y, pill_w, 26.0, 8.0, bg);
            ui.border(px, step_y, pill_w, 26.0, 8.0, border_col);
            let prefix = if done { "✓ " } else { "" };
            let step_txt = format!("{prefix}{name}");
            let fitted = ui.fit_text(Face::Medium, 11.5, &step_txt, pill_w - 10.0);
            let tw = ui.measure(Face::Medium, 11.5, &fitted);
            ui.text(
                Face::Medium,
                11.5,
                px + (pill_w - tw) * 0.5,
                step_y + 5.0,
                if active || done {
                    blaze_text()
                } else {
                    blaze_muted()
                },
                &fitted,
            );
        }
    }

    // Bottom-right MY3 Pause (||) and Cancel (X) rounded square buttons.
    let btn_y = (h - 62.0).max(bar_y + 82.0);
    let cancel_x = content_x + content_w - 46.0;
    let pause_x = cancel_x - 56.0;
    icon_action_button(
        ui,
        targets,
        [pause_x, btn_y, 46.0, 46.0],
        blaze_pause_bg(),
        if paused { Icon::Play } else { Icon::Pause },
        pause_action,
    );
    icon_action_button(
        ui,
        targets,
        [cancel_x, btn_y, 46.0, 46.0],
        blaze_cancel_bg(),
        Icon::Close,
        cancel_action,
    );
}

pub(crate) fn format_downloads(n: u64) -> String {
    if n >= 1_000_000 {
        format!("{:.1}M", n as f64 / 1_000_000.0)
    } else if n >= 1_000 {
        format!("{:.1}K", n as f64 / 1_000.0)
    } else {
        n.to_string()
    }
}

pub(crate) fn capitalize(s: &str) -> String {
    let mut chars = s.chars();
    match chars.next() {
        Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
        None => String::new(),
    }
}

pub(crate) fn wrap_text_lines(ui: &Ui, face: Face, size: f32, text: &str, max_w: f32) -> Vec<String> {
    let mut lines = Vec::new();
    for raw_line in text.lines() {
        let trimmed = raw_line.trim();
        if trimmed.is_empty() {
            if lines.last().is_some_and(|l: &String| !l.is_empty()) {
                lines.push(String::new());
            }
            continue;
        }
        let mut current = String::new();
        for word in trimmed.split_whitespace() {
            let candidate = if current.is_empty() {
                word.to_owned()
            } else {
                format!("{current} {word}")
            };
            if ui.measure(face, size, &candidate) <= max_w || current.is_empty() {
                current = candidate;
            } else {
                lines.push(current);
                current = word.to_owned();
            }
            if lines.len() >= 16 {
                break;
            }
        }
        if !current.is_empty() && lines.len() < 16 {
            lines.push(current);
        }
        if lines.len() >= 16 {
            break;
        }
    }
    if lines.is_empty() {
        lines.push("No additional description available.".into());
    }
    lines
}

impl Launcher {
    fn draw_project_modal(&mut self, ui: &mut Ui, w: f32, h: f32) {
        // The install label asks the catalog about the project the modal is showing, so work it out
        // before the modal is borrowed mutably for drawing.
        let Some(install_label) = self.catalog.project_modal.as_ref().map(|modal| {
            let version = modal
                .versions
                .get(modal.selected_version)
                .map_or_else(|| "Latest".to_owned(), |v| v.number.clone());
            if let Some(bundled) = modal.bundled_id {
                if self.catalog.bundled_enabled.contains(bundled) {
                    "Installed (Tap to Disable)".to_owned()
                } else {
                    format!("Enable Built-in ({version})")
                }
            } else if crate::catalog::source_only_mod(&modal.project) {
                "Native .hnmod temporarily unsupported on Android".to_owned()
            } else if let Some(state) = self.catalog.download_label(&modal.project) {
                state
            } else {
                format!("Download {} ({version})", modal.project.kind.label())
            }
        }) else {
            return;
        };
        let Some(modal) = &mut self.catalog.project_modal else {
            return;
        };
        self.targets.clear();
        self.list_targets.clear();

        // Scrim backdrop
        let alpha = (185.0 * self.anim.modal_anim.clamp(0.2, 1.0)) as u8;
        ui.rounded(
            0.0,
            0.0,
            w,
            h,
            0.0,
            horizon_client::hud::rgba(6, 5, 12, alpha),
        );

        let scale_in = 0.94 + 0.06 * self.anim.modal_anim.clamp(0.0, 1.0);
        let mw = ((w - 44.0).min(640.0) * scale_in).max(280.0);
        let mh = ((h - 36.0).min(385.0) * scale_in).max(230.0);
        let mx = (w - mw) * 0.5;
        let my = (h - mh) * 0.5;

        ui.rounded(mx, my, mw, mh, GLASS_RADIUS, blaze_surface());
        ui.border(mx, my, mw, mh, GLASS_RADIUS, blaze_border());

        // Modal Top Header (PFP + Title + Author/Downloads + Close Button)
        let icon_drawn = if let Some((iw, ih, rgba)) = self.catalog.icons.get(&modal.project.id) {
            ui.image(
                &modal.project.id,
                *iw,
                *ih,
                rgba,
                mx + 18.0,
                my + 16.0,
                50.0,
                50.0,
            )
        } else {
            false
        };
        if !icon_drawn {
            ui.rounded(mx + 18.0, my + 16.0, 50.0, 50.0, 14.0, blaze_accent_soft());
            ui.icon(
                page_icon(self.catalog.page),
                mx + 31.0,
                my + 29.0,
                24.0,
                blaze_accent(),
            );
        }

        let close_rect = [mx + mw - 54.0, my + 16.0, 38.0, 38.0];
        icon_action_button(
            ui,
            &mut self.targets,
            close_rect,
            blaze_surface_high(),
            Icon::Close,
            Action::CloseProjectModal,
        );

        let title_max = (mw - 146.0).max(100.0);
        let fitted_title = ui.fit_text(Face::SemiBold, 18.5, &modal.project.title, title_max);
        ui.text(
            Face::SemiBold,
            18.5,
            mx + 78.0,
            my + 16.0,
            blaze_text(),
            &fitted_title,
        );

        let mut meta_parts = vec![format!("by {}", modal.author)];
        if modal.downloads > 0 {
            meta_parts.push(format!("{} downloads", format_downloads(modal.downloads)));
        }
        if !modal.license.is_empty() {
            meta_parts.push(modal.license.clone());
        }
        if modal.loading {
            meta_parts.push("Syncing Modrinth details...".into());
        }
        let meta_line = meta_parts.join("  •  ");
        let fitted_meta = ui.fit_text(Face::Regular, 12.5, &meta_line, title_max);
        ui.text(
            Face::Regular,
            12.5,
            mx + 78.0,
            my + 41.0,
            blaze_muted(),
            &fitted_meta,
        );

        // Divider under modal header
        ui.rounded(mx + 16.0, my + 74.0, mw - 32.0, 1.0, 0.0, blaze_border());

        // Scrollable Body Area (Description + Gallery Screenshots + Versions List)
        let body_top = my + 82.0;
        let body_bottom = my + mh - 62.0;
        let inner_x = mx + 18.0;
        let inner_w = mw - 36.0;

        let desc_lines =
            wrap_text_lines(ui, Face::Regular, 13.0, &modal.description, inner_w - 8.0);
        let desc_h = (desc_lines.len() as f32 * 18.5).max(24.0);
        let gallery_h = if modal.screenshots.is_empty() {
            0.0
        } else {
            118.0
        };
        let versions_h = 28.0 + (modal.versions.len().min(6) as f32) * 38.0;
        let total_content_h = 24.0 + desc_h + gallery_h + versions_h + 16.0;
        let max_scroll = (total_content_h - (body_bottom - body_top)).max(0.0);
        modal.scroll = modal.scroll.clamp(0.0, max_scroll);
        self.list_view = Some([body_top, body_bottom, max_scroll]);

        ui.set_clip(Some([
            mx + 4.0,
            body_top,
            mw - 8.0,
            (body_bottom - body_top).max(1.0),
        ]));
        let mut cy = body_top - modal.scroll;

        // Section 1: About / Description
        if cy + 20.0 >= body_top && cy <= body_bottom {
            ui.text(
                Face::SemiBold,
                14.0,
                inner_x,
                cy,
                blaze_accent(),
                "About Project",
            );
        }
        cy += 22.0;
        for line in &desc_lines {
            if cy + 18.0 >= body_top && cy <= body_bottom {
                ui.text(Face::Regular, 13.0, inner_x, cy, blaze_text(), line);
            }
            cy += 18.5;
        }
        cy += 8.0;

        // Section 2: Screenshots (from Modrinth gallery)
        if !modal.screenshots.is_empty() {
            if cy + 20.0 >= body_top && cy <= body_bottom {
                ui.text(
                    Face::SemiBold,
                    14.0,
                    inner_x,
                    cy,
                    blaze_accent(),
                    "Screenshots",
                );
            }
            cy += 22.0;
            let count = modal.screenshots.len().min(3) as f32;
            let gap = 10.0;
            let card_w = ((inner_w - gap * (count - 1.0)) / count).clamp(110.0, 200.0);
            let card_h = 84.0;
            if cy + card_h >= body_top && cy <= body_bottom {
                for (idx, (caption, sw, sh, rgba)) in modal.screenshots.iter().take(3).enumerate() {
                    let sx = inner_x + idx as f32 * (card_w + gap);
                    ui.rounded(sx, cy, card_w, card_h, 12.0, blaze_card_bg());
                    ui.border(sx, cy, card_w, card_h, 12.0, blaze_card_border());
                    let key = format!("shot:{}:{idx}", modal.project.id);
                    let _ = ui.image(
                        &key,
                        *sw,
                        *sh,
                        rgba,
                        sx + 3.0,
                        cy + 3.0,
                        card_w - 6.0,
                        card_h - 22.0,
                    );
                    let cap_fit = ui.fit_text(Face::Regular, 11.0, caption, card_w - 12.0);
                    ui.text(
                        Face::Regular,
                        11.0,
                        sx + 6.0,
                        cy + card_h - 17.0,
                        blaze_muted(),
                        &cap_fit,
                    );
                }
            }
            cy += card_h + 12.0;
        }

        // Section 3: Selectable Versions to Download
        if cy + 20.0 >= body_top && cy <= body_bottom {
            ui.text(
                Face::SemiBold,
                14.0,
                inner_x,
                cy,
                blaze_accent(),
                "Available Versions",
            );
        }
        cy += 24.0;
        for (v_idx, ver) in modal.versions.iter().take(6).enumerate() {
            let selected = modal.selected_version == v_idx;
            let row_rect = [inner_x, cy, inner_w, 34.0];
            self.list_targets
                .push((row_rect, Action::SelectModalVersion(v_idx)));
            if cy + 34.0 >= body_top && cy <= body_bottom {
                ui.rounded(
                    inner_x,
                    cy,
                    inner_w,
                    34.0,
                    10.0,
                    if selected {
                        blaze_accent_soft()
                    } else {
                        blaze_card_bg()
                    },
                );
                ui.border(
                    inner_x,
                    cy,
                    inner_w,
                    34.0,
                    10.0,
                    if selected {
                        blaze_accent()
                    } else {
                        blaze_card_border()
                    },
                );
                let ver_title = format!(
                    "{}v{}  •  MC {}  ({})",
                    if selected { "● " } else { "○ " },
                    ver.number,
                    ver.game_versions.first().map_or("26.3", String::as_str),
                    if ver.loaders.is_empty() {
                        modal.project.source.label().to_owned()
                    } else {
                        ver.loaders.join(", ")
                    }
                );
                let fitted_v = ui.fit_text(Face::Medium, 13.0, &ver_title, inner_w - 24.0);
                ui.text(
                    Face::Medium,
                    13.0,
                    inner_x + 12.0,
                    cy + 7.0,
                    if selected {
                        blaze_text()
                    } else {
                        blaze_muted()
                    },
                    &fitted_v,
                );
                if let Some(c) = ui.clip_rect(row_rect) {
                    self.targets.push((c, Action::SelectModalVersion(v_idx)));
                }
            }
            cy += 38.0;
        }

        ui.set_clip(None);
        // Modal Bottom Action Bar
        ui.rounded(
            mx + 16.0,
            my + mh - 56.0,
            mw - 32.0,
            1.0,
            0.0,
            blaze_border(),
        );

        button(
            ui,
            &mut self.targets,
            [mx + 18.0, my + mh - 48.0, 108.0, 38.0],
            "Close",
            Action::CloseProjectModal,
            false,
        );
        button(
            ui,
            &mut self.targets,
            [mx + 136.0, my + mh - 48.0, mw - 154.0, 38.0],
            &install_label,
            Action::InstallModalProject,
            true,
        );
    }

    fn draw_diagnostics_summary_modal(&mut self, ui: &mut Ui, w: f32, h: f32) {
        self.targets.clear();
        self.list_targets.clear();
        self.list_view = None;

        let modal_t = self.anim.modal_anim.clamp(0.25, 1.0);
        ui.rounded(
            0.0,
            0.0,
            w,
            h,
            0.0,
            horizon_client::hud::rgba(4, 6, 14, (198.0 * modal_t) as u8),
        );

        let mw = (w - 28.0).clamp(320.0, 560.0);
        let mh = (h - 24.0).clamp(248.0, 356.0);
        let mx = (w - mw) * 0.5;
        let my = (h - mh) * 0.5 + (1.0 - modal_t) * 10.0;

        ui.rounded(
            mx + 2.0,
            my + 6.0,
            mw,
            mh,
            GLASS_RADIUS,
            horizon_client::hud::rgba(0, 0, 0, 175),
        );
        ui.rounded(mx, my, mw, mh, GLASS_RADIUS, blaze_surface());
        ui.border(mx, my, mw, mh, GLASS_RADIUS, blaze_accent());

        // Header icon & title
        ui.rounded(mx + 18.0, my + 14.0, 36.0, 36.0, 11.0, blaze_accent_soft());
        ui.icon(Icon::Terminal, mx + 27.0, my + 23.0, 18.0, blaze_accent());
        ui.text(
            Face::SemiBold,
            17.0,
            mx + 64.0,
            my + 13.0,
            blaze_text(),
            "Diagnostics & Runtime Health Summary",
        );
        ui.text(
            Face::Regular,
            11.5,
            mx + 64.0,
            my + 33.0,
            blaze_muted(),
            "Live renderer pipeline, profile state, error log & telemetry status",
        );

        ui.rounded(mx + 16.0, my + 58.0, mw - 32.0, 1.0, 0.0, blaze_border());

        // 2x2 Metric Grid Cards
        let c = &self.catalog.config;
        let gx = mx + 18.0;
        let gw = mw - 36.0;
        let half_w = (gw - 10.0) * 0.5;
        let card1_y = my + 68.0;
        let card2_y = card1_y + 52.0;

        let gpu_txt = format!(
            "{}  •  {}% Scale  •  {}",
            c.renderer.label(),
            c.render_scale.percent(),
            c.device.display_label()
        );
        let prof_txt = format!(
            "Profile {}  •  UI {}  •  {}",
            c.profile + 1,
            c.ui_scale_label(),
            c.input_host.label()
        );
        let anim_txt = format!(
            "Launcher Anim: {}  •  Game Anim: {}",
            if c.disable_launcher_animations {
                "OFF"
            } else {
                "ON"
            },
            if c.disable_game_animations {
                "OFF"
            } else {
                "ON"
            }
        );
        let perf_txt = format!(
            "SuperPerf: {}  •  FastUpload: {}  •  ADPF: {}",
            if c.super_perf_mode { "ON" } else { "OFF" },
            if c.fast_chunk_upload { "ON" } else { "OFF" },
            if c.device.performance_hints {
                "ON"
            } else {
                "OFF"
            }
        );

        for (r_idx, (title, val, x_off, y_pos)) in [
            ("GRAPHICS & DISPLAY PIPELINE", gpu_txt.as_str(), gx, card1_y),
            (
                "ACTIVE PROFILE & INPUT HOST",
                prof_txt.as_str(),
                gx + half_w + 10.0,
                card1_y,
            ),
            ("ANIMATION & MOTION STATE", anim_txt.as_str(), gx, card2_y),
            (
                "PERFORMANCE & UPLOAD BOOST",
                perf_txt.as_str(),
                gx + half_w + 10.0,
                card2_y,
            ),
        ]
        .into_iter()
        .enumerate()
        {
            let _ = r_idx;
            ui.rounded(x_off, y_pos, half_w, 46.0, 12.0, blaze_card_bg());
            ui.border(x_off, y_pos, half_w, 46.0, 12.0, blaze_card_border());
            ui.text(
                Face::SemiBold,
                9.5,
                x_off + 10.0,
                y_pos + 6.0,
                blaze_accent(),
                title,
            );
            let fit_v = ui.fit_text(Face::Medium, 11.2, val, half_w - 20.0);
            ui.text(
                Face::Medium,
                11.2,
                x_off + 10.0,
                y_pos + 23.0,
                blaze_text(),
                &fit_v,
            );
        }

        // Summary & Error Log Box
        let log_y = card2_y + 54.0;
        let log_h = (mh - (log_y - my) - 60.0).max(52.0);
        ui.rounded(gx, log_y, gw, log_h, 12.0, blaze_card_bg());
        ui.border(gx, log_y, gw, log_h, 12.0, blaze_card_border());

        let summary_str = crate::diagnostics::summary(&self.home);
        let err_str = std::fs::read_to_string(self.home.join("android-error.txt"))
            .ok()
            .map(|e| crate::diagnostics::redact(&e))
            .unwrap_or_else(|| {
                "None — last session exited cleanly with zero unhandled errors.".into()
            });

        ui.text(
            Face::SemiBold,
            10.0,
            gx + 12.0,
            log_y + 6.0,
            blaze_accent(),
            "DIAGNOSTICS SUMMARY & LAST HANDLED ERROR",
        );
        let fit_sum = ui.fit_text(Face::Regular, 11.0, &summary_str, gw - 24.0);
        ui.text(
            Face::Regular,
            11.0,
            gx + 12.0,
            log_y + 22.0,
            blaze_text(),
            &fit_sum,
        );
        let fit_err = ui.fit_text(
            Face::Mono,
            10.2,
            &format!("Last Error: {err_str}"),
            gw - 24.0,
        );
        ui.text(
            Face::Mono,
            10.2,
            gx + 12.0,
            log_y + 39.0,
            blaze_muted(),
            &fit_err,
        );

        // Bottom Action Buttons: Close | Open Reports | Export ZIP
        let btn_y = my + mh - 46.0;
        let bw3 = (gw - 16.0) / 3.0;
        button(
            ui,
            &mut self.targets,
            [gx, btn_y, bw3, 36.0],
            "Close",
            Action::CloseDiagnosticsSummary,
            false,
        );
        button(
            ui,
            &mut self.targets,
            [gx + bw3 + 8.0, btn_y, bw3, 36.0],
            "Open Reports",
            Action::OpenReportsFolder,
            false,
        );
        button(
            ui,
            &mut self.targets,
            [gx + (bw3 + 8.0) * 2.0, btn_y, bw3, 36.0],
            "Export ZIP",
            Action::ExportDiagnostics,
            true,
        );
    }

    fn draw_fm_modal(&mut self, ui: &mut Ui, w: f32, h: f32, ime_top_dp: Option<f32>) {
        let Some(modal) = self.catalog.fm_modal.clone() else {
            return;
        };
        self.targets.clear();
        self.list_targets.clear();
        self.list_view = None;

        let modal_t = self.anim.modal_anim.clamp(0.2, 1.0);
        ui.rounded(
            0.0,
            0.0,
            w,
            h,
            0.0,
            horizon_client::hud::rgba(6, 5, 12, (185.0 * modal_t) as u8),
        );

        match modal {
            crate::catalog::FmModal::ConfirmDelete { paths } => {
                let mw = (w - 32.0).min(420.0);
                let mh = 190.0;
                let mx = (w - mw) * 0.5;
                let my = (h - mh) * 0.5;
                ui.rounded(mx, my, mw, mh, GLASS_RADIUS, blaze_surface());
                ui.border(mx, my, mw, mh, GLASS_RADIUS, blaze_border());
                ui.icon(Icon::Trash, mx + 20.0, my + 20.0, 22.0, blaze_accent());
                let title = if paths.len() == 1 {
                    "Delete selected item?".to_owned()
                } else {
                    format!("Delete {} selected items?", paths.len())
                };
                ui.text(
                    Face::SemiBold,
                    18.0,
                    mx + 50.0,
                    my + 18.0,
                    blaze_text(),
                    &title,
                );
                let preview_names = paths.iter().take(3).cloned().collect::<Vec<_>>().join(", ");
                ui.text_clipped(
                    Face::Medium,
                    12.5,
                    mx + 20.0,
                    my + 54.0,
                    mw - 40.0,
                    blaze_accent(),
                    &preview_names,
                );
                ui.hint_line(
                    mx + 20.0,
                    my + 80.0,
                    mw - 40.0,
                    "This permanently deletes the selected file(s) from this profile and cannot be undone.",
                    blaze_muted(),
                );
                let bw = (mw - 52.0) * 0.5;
                button(
                    ui,
                    &mut self.targets,
                    [mx + 20.0, my + mh - 54.0, bw, 38.0],
                    "Cancel",
                    Action::FmCloseModal,
                    false,
                );
                button(
                    ui,
                    &mut self.targets,
                    [mx + 32.0 + bw, my + mh - 54.0, bw, 38.0],
                    "Confirm Delete",
                    Action::FmConfirmDelete,
                    true,
                );
            }
            crate::catalog::FmModal::Rename { target_rel } => {
                let mw = (w - 32.0).min(430.0);
                let mh = 196.0;
                let mx = (w - mw) * 0.5;
                let mut my = (h - mh) * 0.5;
                if let Some(ime_y) = ime_top_dp {
                    my = my.min((ime_y - mh - 8.0).max(6.0));
                }
                ui.rounded(mx, my, mw, mh, GLASS_RADIUS, blaze_surface());
                ui.border(mx, my, mw, mh, GLASS_RADIUS, blaze_border());
                ui.text(
                    Face::SemiBold,
                    18.0,
                    mx + 20.0,
                    my + 16.0,
                    blaze_text(),
                    "Rename File or Folder",
                );
                ui.hint_line(
                    mx + 20.0,
                    my + 42.0,
                    mw - 40.0,
                    &format!("Target: /{target_rel} (Slashes and '..' are rejected)"),
                    blaze_muted(),
                );
                let field_r = ui.text_field(
                    mx + 20.0,
                    my + 68.0,
                    mw - 40.0,
                    48.0,
                    "NEW NAME",
                    &self.catalog.fm_input_text,
                    "Enter new filename",
                    self.catalog.editing == Some("fm_rename"),
                );
                self.targets.push((field_r, Action::Field("fm_rename")));
                let bw = (mw - 52.0) * 0.5;
                button(
                    ui,
                    &mut self.targets,
                    [mx + 20.0, my + mh - 52.0, bw, 38.0],
                    "Cancel",
                    Action::FmCloseModal,
                    false,
                );
                button(
                    ui,
                    &mut self.targets,
                    [mx + 32.0 + bw, my + mh - 52.0, bw, 38.0],
                    "Save Name",
                    Action::FmSubmitRename,
                    true,
                );
            }
            crate::catalog::FmModal::NewFolder => {
                let mw = (w - 32.0).min(430.0);
                let mh = 196.0;
                let mx = (w - mw) * 0.5;
                let mut my = (h - mh) * 0.5;
                if let Some(ime_y) = ime_top_dp {
                    my = my.min((ime_y - mh - 8.0).max(6.0));
                }
                ui.rounded(mx, my, mw, mh, GLASS_RADIUS, blaze_surface());
                ui.border(mx, my, mw, mh, GLASS_RADIUS, blaze_border());
                ui.text(
                    Face::SemiBold,
                    18.0,
                    mx + 20.0,
                    my + 16.0,
                    blaze_text(),
                    "Create New Folder",
                );
                let loc = if self.catalog.fm_current_dir.is_empty() {
                    "/".to_owned()
                } else {
                    format!("/{}", self.catalog.fm_current_dir)
                };
                ui.hint_line(
                    mx + 20.0,
                    my + 42.0,
                    mw - 40.0,
                    &format!("Location: {loc}"),
                    blaze_muted(),
                );
                let field_r = ui.text_field(
                    mx + 20.0,
                    my + 68.0,
                    mw - 40.0,
                    48.0,
                    "FOLDER NAME",
                    &self.catalog.fm_input_text,
                    "Enter folder name",
                    self.catalog.editing == Some("fm_new_folder"),
                );
                self.targets.push((field_r, Action::Field("fm_new_folder")));
                let bw = (mw - 52.0) * 0.5;
                button(
                    ui,
                    &mut self.targets,
                    [mx + 20.0, my + mh - 52.0, bw, 38.0],
                    "Cancel",
                    Action::FmCloseModal,
                    false,
                );
                button(
                    ui,
                    &mut self.targets,
                    [mx + 32.0 + bw, my + mh - 52.0, bw, 38.0],
                    "Create Folder",
                    Action::FmSubmitNewFolder,
                    true,
                );
            }
            crate::catalog::FmModal::Details(details) => {
                let mw = (w - 36.0).min(560.0);
                let mh = (h - 32.0).min(340.0);
                let mx = (w - mw) * 0.5;
                let my = (h - mh) * 0.5;
                ui.rounded(mx, my, mw, mh, GLASS_RADIUS, blaze_surface());
                ui.border(mx, my, mw, mh, GLASS_RADIUS, blaze_border());
                ui.text_clipped(
                    Face::SemiBold,
                    17.5,
                    mx + 20.0,
                    my + 14.0,
                    mw - 80.0,
                    blaze_text(),
                    &details.name,
                );
                let close_r = [mx + mw - 50.0, my + 12.0, 34.0, 34.0];
                icon_action_button(
                    ui,
                    &mut self.targets,
                    close_r,
                    blaze_surface_high(),
                    Icon::Close,
                    Action::FmCloseModal,
                );

                let meta_summary = format!(
                    "Path: /{}  •  Kind: {}  •  Size: {} ({} bytes)  •  Modified: {}",
                    details.rel_path,
                    details.kind_label,
                    crate::catalog::format_file_size(details.size_bytes),
                    details.size_bytes,
                    details.modified_label
                );
                ui.hint_line(
                    mx + 20.0,
                    my + 40.0,
                    mw - 40.0,
                    &meta_summary,
                    blaze_accent(),
                );
                ui.rounded(mx + 18.0, my + 62.0, mw - 36.0, 1.0, 0.0, blaze_border());

                let box_y = my + 70.0;
                let box_h = (mh - 126.0).max(60.0);
                ui.rounded(mx + 18.0, box_y, mw - 36.0, box_h, 12.0, blaze_card_bg());
                ui.border(
                    mx + 18.0,
                    box_y,
                    mw - 36.0,
                    box_h,
                    12.0,
                    blaze_card_border(),
                );
                ui.set_clip(Some([mx + 22.0, box_y + 4.0, mw - 44.0, box_h - 8.0]));
                for (i, line) in details.preview_lines.iter().enumerate() {
                    let ly = box_y + 8.0 + i as f32 * 16.0;
                    if ly + 14.0 > box_y + box_h {
                        break;
                    }
                    ui.text_clipped(
                        Face::Mono,
                        10.5,
                        mx + 26.0,
                        ly,
                        mw - 52.0,
                        blaze_text(),
                        line,
                    );
                }
                ui.set_clip(None);

                let btn_y = my + mh - 46.0;
                if let Some(entry_idx) = self
                    .catalog
                    .fm_entries
                    .iter()
                    .position(|e| e.rel_path == details.rel_path)
                {
                    let bw = (mw - 56.0) / 4.0;
                    button(
                        ui,
                        &mut self.targets,
                        [mx + 18.0, btn_y, bw, 34.0],
                        "Rename",
                        Action::FmOpenRename(entry_idx),
                        false,
                    );
                    button(
                        ui,
                        &mut self.targets,
                        [mx + 24.0 + bw, btn_y, bw, 34.0],
                        "Export",
                        Action::FmExportSingle(entry_idx),
                        false,
                    );
                    button(
                        ui,
                        &mut self.targets,
                        [mx + 30.0 + bw * 2.0, btn_y, bw, 34.0],
                        "Delete",
                        Action::FmRequestDeleteSingle(entry_idx),
                        false,
                    );
                    button(
                        ui,
                        &mut self.targets,
                        [mx + 36.0 + bw * 3.0, btn_y, bw, 34.0],
                        "Done",
                        Action::FmCloseModal,
                        true,
                    );
                } else {
                    button(
                        ui,
                        &mut self.targets,
                        [mx + mw - 128.0, btn_y, 110.0, 34.0],
                        "Close",
                        Action::FmCloseModal,
                        true,
                    );
                }
            }
        }
    }
}

impl Launcher {
    pub(crate) fn navigate(&mut self, key: &KeyEvent) -> bool {
        self.navigate_key(&key.logical_key)
    }
    pub(crate) fn navigate_key(&mut self, key: &winit::keyboard::Key) -> bool {
        use horizon_client::gamepad::{self, Button};
        use winit::keyboard::{Key, NamedKey};
        if self.pending_focus.is_some() {
            return true;
        }
        if self.catalog.editing.is_some() || self.targets.is_empty() {
            return false;
        }
        let button = match key {
            Key::Named(NamedKey::ArrowUp) => Button::DPadUp,
            Key::Named(NamedKey::ArrowDown) => Button::DPadDown,
            Key::Named(NamedKey::ArrowLeft) => Button::DPadLeft,
            Key::Named(NamedKey::ArrowRight) => Button::DPadRight,
            Key::Named(NamedKey::Tab) => {
                self.focus = Some(self.focus.map_or(0, |i| (i + 1) % self.targets.len()));
                return true;
            }
            _ => return false,
        };
        if matches!(button, Button::DPadUp | Button::DPadDown)
            && let Some((_, action)) = self.focus.and_then(|i| self.targets.get(i))
            && let Some(index) = self.list_targets.iter().position(|(_, a)| a == action)
        {
            let rects: Vec<_> = self.list_targets.iter().map(|(r, _)| *r).collect();
            if let Some(next) = gamepad::focus_step_strict(rects[index], button, &rects)
                && let Some(view) = self.list_view
            {
                let (rect, action) = self.list_targets[next];
                let scroll = if let Some(chooser) = &mut self.chooser {
                    &mut chooser.scroll
                } else if let Some(modal) = &mut self.catalog.project_modal {
                    &mut modal.scroll
                } else {
                    &mut self.catalog.scroll
                };
                *scroll = super::gesture::reveal(*scroll, rect, view);
                self.pending_focus = Some(action);
                self.focus = None;
                if let Some(window) = &self.window {
                    window.request_redraw();
                }
                return true;
            }
        }
        self.focus = Some(
            if let Some(index) = self.focus.filter(|i| *i < self.targets.len()) {
                let rects: Vec<_> = self.targets.iter().map(|(rect, _)| *rect).collect();
                gamepad::focus_step_rect(rects[index], button, &rects).unwrap_or(index)
            } else {
                0
            },
        );
        true
    }

    pub(crate) fn draw_pages(&mut self) -> Result<()> {
        let Some(mut r) = self.renderer.take() else {
            return Ok(());
        };
        let Some(gui) = self.gui.take() else {
            self.renderer = Some(r);
            return Ok(());
        };
        let res = self.draw_pages_with(&mut r, &gui);
        self.renderer = Some(r);
        self.gui = Some(gui);
        res
    }

    fn draw_pages_with(&mut self, r: &mut Renderer, gui: &game::UiResources) -> Result<()> {
        let (w_px, h_px) = (r.config.width.max(1), r.config.height.max(1));
        let mut b = HudBatch::new([w_px, h_px]);

        self.targets.clear();
        self.list_targets.clear();
        self.list_view = None;
        SLIDER_TRACK_TARGETS.with(|list| list.borrow_mut().clear());

        let cursor_dp = (self.cursor.0 as f32, self.cursor.1 as f32);
        let mut ui = Ui::mobile(&gui.atlas, &mut b, cursor_dp);
        let w = ui.w;
        let h = ui.h;

        let ime_top_dp = if self.catalog.editing.is_some() {
            crate::native_ime::viewport_bottom()
                .map(|bottom_px| (bottom_px as f32 / ui.density).clamp(110.0, ui.h))
        } else {
            None
        };
        let page = self.catalog.page;

        // Top Header Rail
        let top_h = 58.0;
        let top_margin = 4.0;
        let pill_h = 36.0;
        let pill_y = top_margin + (top_h - pill_h) * 0.5;

        // Brand
        let brand_x = 16.0;
        let brand_w = 120.0;
        ui.text(
            Face::SemiBold,
            15.0,
            brand_x,
            pill_y + 1.0,
            blaze_text(),
            "BLAZE",
        );
        ui.text(
            Face::Medium,
            9.5,
            brand_x,
            pill_y + 18.0,
            blaze_accent(),
            "26.3 • RUST ENGINE",
        );

        // Action Deck (Right)
        let eq_btn_w = 36.0;
        let eq_btn_x = w - 16.0 - eq_btn_w;
        ui.rounded(
            eq_btn_x,
            pill_y,
            eq_btn_w,
            pill_h,
            pill_h * 0.5,
            blaze_surface(),
        );
        ui.border(
            eq_btn_x,
            pill_y,
            eq_btn_w,
            pill_h,
            pill_h * 0.5,
            blaze_border(),
        );
        ui.icon(
            Icon::Gamepad,
            eq_btn_x + (eq_btn_w - 18.0) * 0.5,
            pill_y + (pill_h - 18.0) * 0.5,
            18.0,
            blaze_accent(),
        );
        self.targets.push((
            [eq_btn_x, pill_y, eq_btn_w, pill_h],
            Action::OpenControlEditor,
        ));

        let prof_label = format!("Profile {}", self.catalog.config.profile + 1);
        let prof_w = (ui.measure(Face::Medium, 12.0, &prof_label) + 42.0).clamp(100.0, 160.0);
        let prof_x = eq_btn_x - 8.0 - prof_w;
        let prof_y = pill_y;
        ui.rounded(
            prof_x,
            prof_y,
            prof_w,
            pill_h,
            pill_h * 0.5,
            blaze_surface(),
        );
        ui.border(prof_x, prof_y, prof_w, pill_h, pill_h * 0.5, blaze_border());
        ui.text(
            Face::Medium,
            12.0,
            prof_x + 12.0,
            prof_y + 9.0,
            blaze_text(),
            &prof_label,
        );
        ui.icon(
            Icon::ChevronDown,
            prof_x + prof_w - 18.0,
            prof_y + 12.0,
            12.0,
            blaze_muted(),
        );
        self.targets
            .push(([prof_x, prof_y, prof_w, pill_h], Action::Profile));

        // Center Pill Tab Deck (5 tabs: Home, Browse, Manager, Skin, Settings)
        let deck_left = brand_x + brand_w + 10.0;
        let deck_right = prof_x - 10.0;
        let avail_deck_w = (deck_right - deck_left).max(180.0);
        let deck_w = avail_deck_w.min(410.0);
        let deck_x = deck_left + (avail_deck_w - deck_w) * 0.5;
        let deck_y = top_margin + (top_h - 36.0) * 0.5;
        let deck_h = 36.0;

        ui.rounded(
            deck_x,
            deck_y,
            deck_w,
            deck_h,
            deck_h * 0.5,
            blaze_surface(),
        );
        ui.border(deck_x, deck_y, deck_w, deck_h, deck_h * 0.5, blaze_border());

        let tabs: [(Page, &'static str, Icon); 5] = [
            (Page::Play, "Home", Icon::Play),
            (
                if matches!(self.catalog.page, Page::Mods | Page::Packs | Page::Shaders) {
                    self.catalog.page
                } else {
                    Page::Mods
                },
                "Browse",
                Icon::Mods,
            ),
            (Page::Installed, "Manager", Icon::Folder),
            (Page::Skins, "Skin", Icon::User),
            (Page::Settings, "Settings", Icon::Settings),
        ];

        let selected_tab_idx = match self.catalog.page {
            Page::Play => 0,
            Page::Mods | Page::Packs | Page::Shaders => 1,
            Page::Installed => 2,
            Page::Skins => 3,
            Page::Settings => 4,
        };

        let tab_w = (deck_w - 6.0) / 5.0;
        let target_nav_x = deck_x + 3.0 + selected_tab_idx as f32 * tab_w;

        let has_any_modal = self.catalog.project_modal.is_some()
            || self.catalog.fm_modal.is_some()
            || self.chooser.is_some()
            || self.login.visible
            || self.exit_confirmation;
        self.anim.step(
            self.catalog.page,
            self.catalog.settings_category,
            target_nav_x,
            has_any_modal,
            self.catalog.sections,
        );

        let pill_glide_x = self.anim.nav_pill_y.clamp(
            deck_x + 2.0,
            (deck_x + deck_w - tab_w - 2.0).max(deck_x + 2.0),
        );
        ui.rounded(
            pill_glide_x,
            deck_y + 3.0,
            tab_w,
            deck_h - 6.0,
            (deck_h - 6.0) * 0.5,
            blaze_nav_active(),
        );

        for (i, (page_target, name, icon)) in tabs.into_iter().enumerate() {
            let tx = deck_x + 3.0 + i as f32 * tab_w;
            let ty = deck_y + 3.0;
            let th = deck_h - 6.0;
            let selected = i == selected_tab_idx;
            let fg = if selected {
                blaze_text()
            } else {
                blaze_muted()
            };

            if tab_w >= 62.0 {
                ui.icon(icon, tx + 8.0, ty + (th - 14.0) * 0.5, 14.0, fg);
                let label = ui.fit_text(Face::Medium, 12.0, name, tab_w - 26.0);
                ui.text(
                    Face::Medium,
                    12.0,
                    tx + 24.0,
                    ty + (th - 16.0) * 0.5,
                    fg,
                    &label,
                );
            } else {
                ui.icon(
                    icon,
                    tx + (tab_w - 15.0) * 0.5,
                    ty + (th - 15.0) * 0.5,
                    15.0,
                    fg,
                );
            }
            self.targets
                .push(([tx, ty, tab_w, th], Action::Page(page_target)));
        }

        let content_x = 16.0;
        let content_w = (w - 32.0).max(160.0);
        let compact = content_w < 500.0;
        let slide_y = self.anim.page_slide;

        match page {
            Page::Play => {
                draw_home_view(self, &mut ui, content_x, content_w, h, slide_y);
            }
            Page::Mods | Page::Packs | Page::Shaders => {
                draw_browse_view(self, &mut ui, content_x, content_w, h, slide_y);
            }
            Page::Installed => {
                draw_manager_view(self, &mut ui, content_x, content_w, h, compact, slide_y);
            }
            Page::Skins => {
                draw_skin_view(self, &mut ui, content_x, content_w, slide_y);
            }
            Page::Settings => {
                draw_settings_view(
                    self, &mut ui, content_x, content_w, h, compact, slide_y, ime_top_dp,
                );
            }
        }

        // Modals & Overlays
        if self.catalog.project_modal.is_some() {
            self.draw_project_modal(&mut ui, w, h);
        }

        if self.login.visible {
            self.list_targets.clear();
            self.list_view = None;
            self.login.draw(
                &mut ui,
                &mut self.targets,
                &self.catalog.config.name,
                self.catalog.editing,
                self.anim.modal_anim,
            );
        }

        if let Some(chooser) = &mut self.chooser {
            chooser.draw(&mut ui, &mut self.targets, self.anim.modal_anim);
            self.list_targets = chooser.navigation(ui.w);
            self.list_view = Some(chooser.viewport(ui.h));
        }

        if self.catalog.fm_modal.is_some() {
            self.draw_fm_modal(&mut ui, w, h, ime_top_dp);
        }

        if self.diagnostics_modal_open {
            self.draw_diagnostics_summary_modal(&mut ui, w, h);
        }

        // Starling Voice Chat Banner Overlay
        if !self.catalog.starling_notice_dismissed && page == Page::Play && self.worker.is_none() {
            let banner_h = 32.0;
            let banner_y = h - banner_h - 12.0;
            let banner_w = (content_w - 24.0).min(560.0);
            let banner_x = content_x + (content_w - banner_w) * 0.5;

            ui.rounded(
                banner_x,
                banner_y,
                banner_w,
                banner_h,
                8.0,
                color::hex(0x0E131C, 235),
            );
            ui.border(
                banner_x,
                banner_y,
                banner_w,
                banner_h,
                8.0,
                color::hex(0x38BDF8, 80),
            );

            ui.icon(
                Icon::Flask,
                banner_x + 10.0,
                banner_y + 8.0,
                16.0,
                blaze_accent(),
            );
            ui.text(
                Face::SemiBold,
                11.0,
                banner_x + 32.0,
                banner_y + 9.0,
                blaze_text(),
                "Built-in Starling Voice Chat enabled",
            );

            button(
                &mut ui,
                &mut self.targets,
                [banner_x + banner_w - 74.0, banner_y + 4.0, 64.0, 24.0],
                "Dismiss",
                Action::DismissStarlingNotice,
                false,
            );
        }

        if self.exit_confirmation {
            self.targets.clear();
            self.list_targets.clear();
            self.list_view = None;
            let modal_t = self.anim.modal_anim.clamp(0.2, 1.0);
            ui.rounded(
                0.0,
                0.0,
                w,
                h,
                0.0,
                horizon_client::hud::rgba(0, 0, 0, (180.0 * modal_t) as u8),
            );
            let scale_in = 0.94 + 0.06 * modal_t;
            let width = (w - 32.0).min(360.0) * scale_in;
            let x = (w - width) * 0.5;
            let y = (h - 160.0) * 0.5 + (1.0 - modal_t) * 8.0;
            ui.rounded(x, y, width, 160.0, GLASS_RADIUS, blaze_surface());
            ui.border(x, y, width, 160.0, GLASS_RADIUS, blaze_border());
            ui.text(
                Face::SemiBold,
                20.0,
                x + 22.0,
                y + 24.0,
                blaze_text(),
                "Exit Horizon Android?",
            );
            let bw = (width - 52.0) * 0.5;
            button(
                &mut ui,
                &mut self.targets,
                [x + 20.0, y + 94.0, bw, 42.0],
                "Cancel",
                Action::Dismiss,
                false,
            );
            button(
                &mut ui,
                &mut self.targets,
                [x + 32.0 + bw, y + 94.0, bw, 42.0],
                "Exit",
                Action::ConfirmExit,
                true,
            );
        }

        if let Some(action) = self.pending_focus.take() {
            self.focus = self.targets.iter().position(|(_, a)| *a == action);
        }
        if let Some(index) = self.focus
            && let Some(([x, y, w, h], _)) = self.targets.get(index)
        {
            ui.border(*x - 2.0, *y - 2.0, *w + 4.0, *h + 4.0, 12.0, blaze_accent());
            self.cursor = (
                f64::from((x + w / 2.0) * ui.density),
                f64::from((y + h / 2.0) * ui.density),
            );
        }
        if self.mouse_down || self.drag.as_ref().is_some_and(|drag| !drag.moved) {
            let point = [
                self.cursor.0 as f32 / ui.density,
                self.cursor.1 as f32 / ui.density,
            ];
            if let Some(([x, y, w, h], _)) = self.targets.iter().rev().find(|(rect, _)| {
                point[0] >= rect[0]
                    && point[0] <= rect[0] + rect[2]
                    && point[1] >= rect[1]
                    && point[1] <= rect[1] + rect[3]
            }) {
                ui.rounded(
                    *x,
                    *y,
                    *w,
                    *h,
                    (*h * 0.42).clamp(10.0, 22.0),
                    horizon_client::hud::rgba(255, 255, 255, 36),
                );
            }
        }
        if !self.mouse_down
            && self.drag.is_none()
            && let Some((action, time)) = self.feedback
            && time.elapsed() < std::time::Duration::from_millis(160)
            && let Some(([x, y, w, h], _)) = self.targets.iter().find(|(_, a)| *a == action)
        {
            ui.rounded(
                *x,
                *y,
                *w,
                *h,
                (*h * 0.42).clamp(10.0, 22.0),
                horizon_client::hud::rgba(255, 255, 255, 36),
            );
        }
        crate::documents::draw_ui(&mut ui);
        if !self.anim.intro_done {
            let session_prog = if self.session_worker.is_some() {
                0.30
            } else {
                0.55
            };
            let loading_progress = (0.45f32 + session_prog).clamp(0.0, 1.0);
            if loading_progress >= 0.999 && self.anim.intro_elapsed >= 1.55 {
                self.anim.intro_done = true;
            } else {
                let intro_status = if self.session_worker.is_some() {
                    "Syncing account session & skins..."
                } else if self.catalog.search_worker_active() {
                    "Loading content catalog..."
                } else {
                    ""
                };
                ui.draw_horizon_intro(self.anim.intro_elapsed, loading_progress, intro_status);
                if loading_progress >= 0.999 {
                    self.targets.clear();
                    self.list_targets.clear();
                    self.targets.push(([0.0, 0.0, w, h], Action::SkipIntro));
                }
            }
        }
        ui.finish();

        if !r.render(&game::loading_frame(&b))? && !b.patches.is_empty() {
            // A suspended/lost surface skips the frame, including its glyph
            // uploads. Rebuild them next time instead of caching invisible text.
            horizon_client::ui_theme::reset_atlas();
        }
        Ok(())
    }
    pub(crate) fn activate(&mut self, events: &ActiveEventLoop) -> Result<()> {
        if self.session_worker.is_some() && self.chooser.is_none() && !self.exit_confirmation {
            return Ok(());
        }
        let (Some(r), Some(gui)) = (&self.renderer, &self.gui) else {
            return Ok(());
        };
        let mut b = HudBatch::new([r.config.width, r.config.height]);
        let ui = Ui::mobile(&gui.atlas, &mut b, (0.0, 0.0));
        let p = self.focus.and_then(|index| self.targets.get(index)).map_or(
            [
                self.cursor.0 as f32 / ui.density,
                self.cursor.1 as f32 / ui.density,
            ],
            |([x, y, w, h], _)| [x + w / 2.0, y + h / 2.0],
        );
        let action = self
            .targets
            .iter()
            .rev()
            .find(|([x, y, w, h], _)| {
                (*x..*x + *w).contains(&p[0]) && (*y..*y + *h).contains(&p[1])
            })
            .map(|(_, a)| *a);
        if crate::documents::busy() {
            return Ok(());
        }
        if self.catalog.busy()
            && !matches!(
                action,
                Some(
                    Action::PauseCatalogDownload
                        | Action::CancelCatalogDownload
                        | Action::Page(_)
                        | Action::Account
                )
            )
        {
            return Ok(());
        }
        self.feedback = action.map(|action| (action, std::time::Instant::now()));
        self.focus = None;
        if action.is_some() && horizon_client::official_install::ready(&self.home) {
            let audio = self.audio.get_or_insert_with(|| {
                let mut audio = horizon_client::audio::Audio::new(
                    &self.home.join(".local/horizon-client/26.3"),
                );
                let settings = horizon_client::settings::Settings::load(
                    &self
                        .catalog
                        .config
                        .game_home(&self.home)
                        .join("options.txt"),
                );
                audio.master = settings.volumes[0];
                let mut volumes = settings.volumes;
                volumes[0] = 1.0;
                horizon_client::settings::set_source_volumes(volumes);
                audio
            });
            audio.play_button_click();
        }
        match action {
            Some(Action::PauseInstall) => {
                let next = !self.install_paused.load(Ordering::Relaxed);
                self.install_paused.store(next, Ordering::Relaxed);
            }
            Some(Action::CancelInstall) => {
                self.install_cancelled.store(true, Ordering::Relaxed);
                self.install_paused.store(false, Ordering::Relaxed);
                self.worker = None;
                self.catalog.status = "Game preparation cancelled.".into();
            }
            Some(Action::PauseCatalogDownload) => {
                self.catalog.toggle_pause();
            }
            Some(Action::CancelCatalogDownload) => {
                self.catalog.cancel_download();
            }
            Some(Action::ConfirmExit) => {
                events.exit();
            }
            Some(Action::SettingsCat(cat)) => {
                self.catalog.settings_category = cat;
                self.catalog.scroll = 0.0;
                self.anim.trigger_page_transition();
            }
            Some(Action::OpenProject(index)) => {
                if let Some(project) = self.catalog.results.get(index).cloned() {
                    self.catalog
                        .open_project_modal(&self.home, project, None, Some(index));
                }
            }
            Some(Action::OpenBundledProject(id)) => {
                if let Some((_, title, desc)) = horizon_client::mod_list::ANDROID_BUNDLED
                    .iter()
                    .find(|(bid, _, _)| *bid == id)
                {
                    let project = crate::browser::Project {
                        source: crate::browser::SourceId::Horizon,
                        id: (*id).to_owned(),
                        slug: (*id).to_owned(),
                        title: (*title).to_owned(),
                        summary: (*desc).to_owned(),
                        author: "Horizon Built-in".into(),
                        downloads: 0,
                        icon: None,
                        page: String::new(),
                        kind: crate::browser::Kind::Mod,
                        license: "MIT / Apache-2.0".into(),
                        game_versions: vec![crate::browser::GAME.into()],
                        loaders: vec!["horizon".into()],
                    };
                    self.catalog
                        .open_project_modal(&self.home, project, Some(id), None);
                }
            }
            Some(Action::CloseProjectModal) => {
                self.catalog.project_modal = None;
                self.catalog.modal_worker = None;
            }
            Some(Action::SelectModalVersion(v_idx)) => {
                if let Some(modal) = &mut self.catalog.project_modal {
                    modal.selected_version = v_idx;
                }
            }
            Some(Action::InstallModalProject) => {
                if let Some(modal) = self.catalog.project_modal.take() {
                    self.catalog.modal_worker = None;
                    if let Some(id) = modal.bundled_id {
                        self.toggle_bundled_mod(id)?;
                    } else if let Some(idx) = modal.catalog_index {
                        self.catalog.install(&self.home, idx);
                    }
                }
            }
            Some(Action::UiScale) => self.open_chooser(chooser::Kind::UiScale),
            Some(Action::ChooseUiScale(pct)) => {
                self.catalog.config.ui_scale_percent = pct;
                horizon_client::ui_theme::set_ui_scale_percent(pct);
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
                self.catalog.status = format!(
                    "Launcher UI scale set to {}.",
                    self.catalog.config.ui_scale_label()
                );
            }
            // Video settings mutations
            Some(Action::RenderScaleDelta(delta)) => {
                let next_pct = (self.catalog.config.render_scale.percent() as i32 + delta)
                    .clamp(25, 100) as u32;
                let scale = crate::display::RenderScale::new(next_pct);
                self.catalog.config.render_scale = scale;
                let _ = self.catalog.config.save(&self.home);
                crate::display::set_scale(scale);
                self.modify_game_settings(|s| {
                    s.horizon.resolution_scale = next_pct;
                });
                self.modify_touch_settings(|t| {
                    t.resolution_scale_percent = next_pct;
                });
            }
            Some(Action::RenderDistDelta(delta)) => {
                self.modify_game_settings(|s| {
                    s.render_distance = (s.render_distance + delta).clamp(2, 32);
                });
            }
            Some(Action::SimDistDelta(delta)) => {
                self.modify_game_settings(|s| {
                    s.simulation_distance = (s.simulation_distance + delta).clamp(5, 32);
                });
            }
            Some(Action::FovDelta(delta)) => {
                self.modify_game_settings(|s| {
                    s.fov = (s.fov + delta).clamp(30.0, 110.0);
                });
            }
            Some(Action::CycleMaxFps) => {
                self.modify_game_settings(|s| {
                    s.max_fps = match s.max_fps {
                        0..=45 => 60,
                        46..=75 => 90,
                        76..=105 => 120,
                        106..=200 => 260,
                        _ => 30,
                    };
                });
            }
            Some(Action::CycleGraphicsPreset) => {
                self.modify_game_settings(|s| {
                    s.graphics_preset = match s.graphics_preset.as_str() {
                        "fast" => "fancy".into(),
                        "fancy" => "fabulous".into(),
                        _ => "fast".into(),
                    };
                });
            }
            Some(Action::ToggleVsync) => {
                self.modify_game_settings(|s| s.vsync = !s.vsync);
            }
            Some(Action::ToggleSmoothLighting) => {
                self.modify_game_settings(|s| s.smooth_lighting = !s.smooth_lighting);
            }
            Some(Action::CycleClouds) => {
                self.modify_game_settings(|s| {
                    s.clouds = match s.clouds.as_str() {
                        "false" => "fast".into(),
                        "fast" => "true".into(),
                        _ => "false".into(),
                    };
                });
            }
            Some(Action::ToggleMenuBlur) => {
                self.modify_game_settings(|s| {
                    s.menu_blur = if s.menu_blur > 0 { 0 } else { 4 };
                });
            }
            // Experimental settings mutations
            Some(Action::ToggleSuperPerf) => {
                self.catalog.config.super_perf_mode = !self.catalog.config.super_perf_mode;
                if self.catalog.config.super_perf_mode {
                    self.catalog.config.battery_perf_mode = false;
                    self.catalog.config.renderer = crate::catalog::RendererMode::Vulkan;
                }
                horizon_client::ui_theme::motion::set_reduced_motion(
                    self.catalog.config.super_perf_mode || self.catalog.config.battery_perf_mode,
                );
                self.catalog.config.save(&self.home)?;
                self.catalog.config.apply_experimental_to_game(&self.home);
                self.catalog.status = if self.catalog.config.super_perf_mode {
                    "Super Performance Mode enabled: fast chunk culling, minimal particles & Vulkan batching."
                } else {
                    "Super Performance Mode disabled."
                }
                .into();
            }
            Some(Action::ToggleBatteryPerf) => {
                self.catalog.config.battery_perf_mode = !self.catalog.config.battery_perf_mode;
                if self.catalog.config.battery_perf_mode {
                    self.catalog.config.super_perf_mode = false;
                    self.catalog.config.max_fps_mode = false;
                }
                horizon_client::ui_theme::motion::set_reduced_motion(
                    self.catalog.config.super_perf_mode || self.catalog.config.battery_perf_mode,
                );
                self.catalog.config.save(&self.home)?;
                self.catalog.config.apply_experimental_to_game(&self.home);
                self.catalog.status = if self.catalog.config.battery_perf_mode {
                    "Battery Performance Mode enabled: 60 FPS cap with VSync and balanced workers."
                } else {
                    "Battery Performance Mode disabled."
                }
                .into();
            }
            Some(Action::ToggleMaxFpsMode) => {
                self.catalog.config.max_fps_mode = !self.catalog.config.max_fps_mode;
                if self.catalog.config.max_fps_mode {
                    self.catalog.config.battery_perf_mode = false;
                }
                self.catalog.config.save(&self.home)?;
                self.catalog.config.apply_experimental_to_game(&self.home);
                self.catalog.status = if self.catalog.config.max_fps_mode {
                    "Max FPS Mode enabled: 260 FPS uncapped with VSync disabled."
                } else {
                    "Max FPS Mode disabled."
                }
                .into();
            }
            Some(Action::ToggleZeroLatencyTouch) => {
                self.catalog.config.zero_latency_touch = !self.catalog.config.zero_latency_touch;
                self.catalog.config.save(&self.home)?;
                self.catalog.status = if self.catalog.config.zero_latency_touch {
                    "Zero-Latency Touch Pipeline active."
                } else {
                    "Standard touch pipeline active."
                }
                .into();
            }
            Some(Action::ToggleExperimentalLeaves) => {
                self.catalog.config.experimental_leaf_culling ^= true;
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::ToggleExperimentalSvc) => {
                self.catalog.config.svc_enabled ^= true;
                self.catalog.config.save(&self.home)?;
                horizon_client::android_runtime::set_experimental_svc(
                    self.catalog.config.svc_enabled,
                    self.catalog.config.svc_rnnoise,
                );
                self.catalog.status = if self.catalog.config.svc_enabled {
                    "Built-in Simple Voice Chat enabled."
                } else {
                    "Built-in Simple Voice Chat disabled."
                }
                .into();
            }
            Some(Action::ToggleExperimentalSvcRnnoise) => {
                self.catalog.config.svc_rnnoise ^= true;
                self.catalog.config.save(&self.home)?;
                horizon_client::android_runtime::set_experimental_svc(
                    self.catalog.config.svc_enabled,
                    self.catalog.config.svc_rnnoise,
                );
                self.catalog.status = if self.catalog.config.svc_rnnoise {
                    "SVC RNNoise & echo suppression filter enabled."
                } else {
                    "SVC RNNoise & echo suppression filter disabled."
                }
                .into();
            }
            Some(Action::ToggleFastChunkUpload) => {
                self.catalog.config.fast_chunk_upload = !self.catalog.config.fast_chunk_upload;
                self.catalog.config.save(&self.home)?;
                self.catalog.config.apply_experimental_to_game(&self.home);
                self.catalog.status = if self.catalog.config.fast_chunk_upload {
                    "Fast Chunk Mesh Upload Boost active."
                } else {
                    "Standard chunk mesh upload active."
                }
                .into();
            }
            // Controls & Control Editor actions
            Some(Action::SelectControlMode(style)) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let mut touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                touch.switch_control_system(&game_home, style);
                self.catalog.status = format!(
                    "Control Mode set to {} (Active preset: '{}').",
                    style.label(),
                    touch.preset_name
                );
            }
            Some(Action::OpenControlEditor) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let mut tc = horizon_client::touch::TouchControls::default();
                tc.settings = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                tc.open_editor();
                self.control_editor = Some(tc);
            }
            Some(Action::CloseControlEditor) => {
                if let Some(mut tc) = self.control_editor.take() {
                    let game_home = self.catalog.config.game_home(&self.home);
                    if !tc.settings.is_default_preset() {
                        let _ = tc.settings.save_replace_preset(&game_home);
                    } else {
                        let _ = tc.settings.save_to(&game_home.join("horizon-touch.json"));
                    }
                    self.catalog.status = format!(
                        "Saved {} preset '{}'.",
                        tc.settings.control_style.label(),
                        tc.settings.preset_name
                    );
                }
            }
            Some(Action::ControlProfileChooser) => {
                self.open_chooser(chooser::Kind::ControlProfile);
            }
            Some(Action::ChooseControlProfile(idx)) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let mut touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                let style = touch.control_style;
                let presets = horizon_client::touch::Settings::list_presets(&game_home, style);
                if let Some(meta) = presets.get(idx as usize) {
                    touch.select_preset(&game_home, style, &meta.id);
                    self.catalog.status = format!(
                        "Selected {} preset '{}' ({}).",
                        style.label(),
                        meta.name,
                        meta.description
                    );
                }
                self.chooser = None;
            }
            Some(Action::CopyActivePreset) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                self.catalog.config.preset_edit_mode = "copy".into();
                self.catalog.config.custom_profile_name = format!("{} Copy", touch.preset_name);
                self.catalog.config.custom_profile_desc = touch.preset_description.clone();
                self.catalog.editing = Some("profile_name");
                crate::platform::show(&self.catalog.config.custom_profile_name);
                self.catalog.status = format!(
                    "Step 1/2: Enter new {} preset name and press Enter.",
                    touch.control_style.label()
                );
            }
            Some(Action::EditActivePresetMeta) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                if touch.is_default_preset() {
                    self.catalog.status = format!(
                        "{} Default preset is protected and cannot be renamed or overwritten. Use 'Copy Preset' to create an editable copy.",
                        touch.control_style.label()
                    );
                } else {
                    self.catalog.config.preset_edit_mode = "edit".into();
                    self.catalog.config.custom_profile_name = touch.preset_name.clone();
                    self.catalog.config.custom_profile_desc = touch.preset_description.clone();
                    self.catalog.editing = Some("profile_name");
                    crate::platform::show(&self.catalog.config.custom_profile_name);
                    self.catalog.status = format!(
                        "Step 1/2: Edit {} preset name and press Enter.",
                        touch.control_style.label()
                    );
                }
            }
            Some(Action::SaveControlProfileAsNew) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                self.catalog.config.preset_edit_mode = "copy".into();
                self.catalog.config.custom_profile_name = format!("{} Copy", touch.preset_name);
                self.catalog.config.custom_profile_desc = touch.preset_description.clone();
                self.catalog.editing = Some("profile_name");
                crate::platform::show(&self.catalog.config.custom_profile_name);
                self.catalog.status = format!(
                    "Step 1/2: Enter new {} preset name and press Enter.",
                    touch.control_style.label()
                );
            }
            Some(Action::ExportControlProfile) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                match touch.export_profile(&self.home, &game_home, &touch.preset_id) {
                    Ok(path) => {
                        let file_name = path
                            .file_name()
                            .unwrap_or_default()
                            .to_string_lossy()
                            .into_owned();
                        let _ = crate::platform::open_documents("reports");
                        self.catalog.status = format!(
                            "Exported '{}' to Reports & controls/{}/{}.json.",
                            file_name,
                            touch.control_style.storage_subdir(),
                            touch.preset_id
                        );
                    }
                    Err(e) => self.catalog.status = e.to_string(),
                }
            }
            Some(Action::ImportControlProfile) => {
                let game_home = self.catalog.config.game_home(&self.home);
                let touch = horizon_client::touch::Settings::load_from(
                    &game_home.join("horizon-touch.json"),
                );
                let _ = std::fs::create_dir_all(
                    game_home
                        .join("controls")
                        .join(touch.control_style.storage_subdir()),
                );
                let _ = crate::platform::open_documents(crate::document_store::ROOT);
                self.catalog.status = format!(
                    "Place any .json preset file in controls/{}/ and select it under {} Preset Selector.",
                    touch.control_style.storage_subdir(),
                    touch.control_style.label()
                );
            }
            Some(Action::MouseSensDelta(delta)) => {
                self.modify_game_settings(|s| {
                    s.sensitivity = (s.sensitivity + delta).clamp(0.05, 1.0);
                });
            }
            Some(Action::TouchSensDelta(delta)) => {
                self.modify_touch_settings(|t| {
                    t.sensitivity = (t.sensitivity + delta).clamp(0.10, 3.0);
                });
            }
            Some(Action::VirtualMouseSpeedDelta(delta)) => {
                self.modify_touch_settings(|t| {
                    t.virtual_mouse_sensitivity =
                        (t.virtual_mouse_sensitivity + delta).clamp(0.25, 3.0);
                });
            }
            Some(Action::ButtonScaleDelta(delta)) => {
                self.modify_touch_settings(|t| {
                    t.button_scale = (t.button_scale + delta).clamp(0.60, 1.60);
                });
            }
            Some(Action::ToggleControlScheme) => {
                self.modify_touch_settings(|t| {
                    t.mode = match t.mode {
                        horizon_client::touch::Mode::Crosshair => horizon_client::touch::Mode::Tap,
                        horizon_client::touch::Mode::Tap => horizon_client::touch::Mode::Dpad,
                        horizon_client::touch::Mode::Dpad => horizon_client::touch::Mode::Crosshair,
                    };
                });
            }
            Some(Action::ToggleSplitControls) => {
                self.modify_touch_settings(|t| t.toggle_split_touch());
            }
            Some(Action::ToggleSprintLatch) => {
                self.modify_touch_settings(|t| t.sprint_toggle = !t.sprint_toggle);
            }
            Some(Action::ToggleLeftHanded) => {
                self.modify_touch_settings(|t| t.left_handed = !t.left_handed);
            }
            Some(Action::ToggleInvertY) => {
                let mut next = false;
                self.modify_game_settings(|s| {
                    s.invert_y = !s.invert_y;
                    next = s.invert_y;
                });
                self.modify_touch_settings(|t| t.invert_y = next);
            }
            Some(Action::ToggleGyro) => {
                self.modify_touch_settings(|t| t.gyroscope = !t.gyroscope);
            }
            Some(Action::GyroSensDelta(delta)) => {
                self.modify_touch_settings(|t| {
                    t.gyro_sensitivity = (t.gyro_sensitivity + delta).clamp(0.10, 3.0);
                });
            }
            Some(Action::ToggleVirtualMouseEnabled) => {
                self.modify_touch_settings(|t| {
                    t.virtual_mouse_enabled = !t.virtual_mouse_enabled;
                });
            }
            Some(Action::PointerSizeDelta(delta)) => {
                self.modify_touch_settings(|t| {
                    t.virtual_mouse_pointer_scale =
                        (t.virtual_mouse_pointer_scale + delta).clamp(0.50, 2.00);
                });
            }
            Some(Action::CycleJoystickZone) => {
                self.modify_touch_settings(|t| {
                    t.joystick_style.move_area = t.joystick_style.move_area.next();
                });
            }
            Some(Action::ToggleTouchUiVibrate) => {
                self.modify_touch_settings(|t| t.ui_vibrate = !t.ui_vibrate);
            }
            Some(Action::ToggleControlHub) => {
                self.modify_touch_settings(|t| t.show_control_hub = !t.show_control_hub);
            }
            Some(Action::ToggleTouchVibrate) => {
                self.modify_touch_settings(|t| t.vibrate = !t.vibrate);
            }
            Some(Action::ToggleAutoJump) => {
                self.modify_game_settings(|s| s.auto_jump = !s.auto_jump);
            }
            Some(Action::ToggleInventoryControls) => {
                self.modify_touch_settings(|t| t.inventory_controls = !t.inventory_controls);
            }
            Some(Action::Immersive) => {
                self.catalog.config.screen.immersive ^= true;
                self.catalog.config.save(&self.home)?;
                self.configure_screen();
            }
            Some(Action::Cutout) => {
                self.catalog.config.screen.avoid_cutout ^= true;
                self.catalog.config.save(&self.home)?;
                self.configure_screen();
            }
            Some(Action::Aspect) => self.open_chooser(chooser::Kind::Aspect),
            Some(Action::ChooseAspect(aspect)) => {
                self.catalog.config.screen.aspect = aspect;
                self.catalog.config.save(&self.home)?;
                self.configure_screen();
                self.chooser = None;
            }
            Some(Action::CacheBudget) => self.open_chooser(chooser::Kind::CacheBudget),
            Some(Action::ChooseCacheBudget(mib)) => {
                self.catalog.config.cache_budget_mib = mib.min(1024);
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
                self.catalog.status = "Disposable cache budget saved. Applies on next Play; not a total RAM or VRAM limit.".into();
            }
            Some(Action::ToggleBundled(id)) => {
                self.toggle_bundled_mod(id)?;
            }
            Some(Action::StarlingDownload) => {
                if !crate::platform::open_url(
                    "https://github.com/JuanLuci/starling/releases/latest",
                ) {
                    self.catalog.status =
                        "Open github.com/JuanLuci/starling/releases to download Starling.".into();
                }
            }
            Some(Action::GameFiles) => {
                self.catalog.status = if crate::platform::open_documents(crate::document_store::ROOT) {
                    "Game files opened. Worlds, mods, packs, shaders and reports are available here."
                } else { "Open system Files > Horizon Android." }.into();
            }
            Some(Action::WorldFiles) => {
                let id = crate::document_store::folder_id(self.catalog.config.profile, "worlds");
                self.catalog.status = if crate::platform::open_documents(&id) {
                    "In Android Files, select a complete world folder, then Copy to a folder you choose. Keep Horizon Android installed until you verify your worlds in Starling."
                } else { "Open system Files > Horizon Android > Worlds. Copy complete world folders while the game is closed." }.into();
            }
            Some(Action::WorkerLimit) => self.open_chooser(chooser::Kind::WorkerLimit),
            Some(Action::ChooseWorkerLimit(limit)) => {
                self.catalog.config.worker_limit = limit.min(64);
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
                self.catalog.status = "Chunk worker ceiling saved. Applies on the next Play; other engine threads are unchanged.".into();
            }
            Some(Action::DisplayHz) => self.open_chooser(chooser::Kind::DisplayHz),
            Some(Action::ChooseDisplayHz(hz)) => {
                self.catalog.config.device.display_hz = hz;
                self.catalog.config.device.sanitize();
                self.catalog.config.save(&self.home)?;
                crate::device_runtime::configure(self.catalog.config.device);
                self.chooser = None;
            }
            Some(Action::PerformanceHints) => {
                self.catalog.config.device.performance_hints ^= true;
                self.catalog.status="ADPF gives Android CPU frame-work hints on supported devices. It does not cap FPS or guarantee a speedup.".into();
                self.catalog.config.save(&self.home)?;
                crate::device_runtime::configure(self.catalog.config.device);
            }
            Some(Action::GpuProfile) => {
                self.catalog.config.device.gpu_profile ^= true;
                self.catalog.status="Detailed GPU profiling records diagnostics and has overhead. Reports remain on this device.".into();
                self.catalog.config.save(&self.home)?;
                crate::device_runtime::configure(self.catalog.config.device);
            }

            Some(Action::ModSource(origin)) => {
                self.catalog.config.mod_source = origin;
                self.catalog.config.save(&self.home)?;
                self.catalog.offset = 0;
                self.catalog.browse(&self.home);
            }
            Some(Action::ShaderSource(origin)) => {
                self.catalog.config.shader_source = origin;
                self.catalog.config.save(&self.home)?;
                self.catalog.offset = 0;
                self.catalog.browse(&self.home);
            }
            Some(Action::OpenFolder(kind)) => {
                crate::platform::hide();
                let folder = match kind {
                    horizon_client::content_files::Kind::Mods => "mods",
                    horizon_client::content_files::Kind::Packs => "packs",
                    horizon_client::content_files::Kind::Shaders => "shaders",
                };
                let id = crate::document_store::folder_id(self.catalog.config.profile, folder);
                self.catalog.status = if crate::platform::open_documents(&id) {
                    "Opened in Files. Return here after copying files.".into()
                } else {
                    "Open the system Files app and choose Horizon Android.".into()
                };
            }
            Some(Action::Import(kind)) => {
                crate::platform::hide();
                let home = self.catalog.config.game_home(&self.home);
                if let Err(e) = crate::documents::begin(kind, home) {
                    self.catalog.status = e;
                }
            }
            Some(Action::ToggleSection(i)) => {
                self.catalog.sections[i] = !self.catalog.sections[i];
                self.catalog.scroll = 0.0;
            }
            Some(Action::ToggleLocal(i)) => self.catalog.toggle_local(&self.home, i),
            Some(Action::DeleteLocal(i)) => {
                self.catalog.pending_delete = self.catalog.local.get(i).cloned();
                if let Some(item) = &self.catalog.pending_delete {
                    self.chooser = Some(super::chooser::Chooser::confirm_delete(item.file.clone()));
                }
            }
            Some(Action::ConfirmDeleteLocal) => {
                self.catalog.remove_local(&self.home);
                self.chooser = None;
            }
            Some(Action::Repository) => {
                self.catalog.status = if crate::platform::open_repository() {
                    "Repository opened in browser."
                } else {
                    "No browser available to open the repository."
                }
                .into();
            }
            Some(Action::Account) => {
                self.catalog.editing = None;
                crate::platform::hide();
                self.login.refresh_status();
                self.login.visible = true;
                self.login.offline = false;
                self.login.ely = false;
                if self.login.signed_in {
                    // Already signed in: open the selector, with the current
                    // Microsoft/Ely.by account called out, instead of asking
                    // the player to choose a provider again.
                    self.login.providers = false;
                    self.login.choosing = true;
                    self.login.account_offset = 0;
                } else {
                    self.login.providers = true;
                    self.login.choosing = false;
                }
            }
            Some(Action::AddAccount) => {
                self.login.choosing = false;
                self.login.providers = true;
                self.login.offline = false;
                self.login.ely = false;
                self.login.visible = true;
            }
            Some(Action::OfflineAccount | Action::MicrosoftAccount) => {
                self.login.providers = false;
                self.login.offline = matches!(action, Some(Action::OfflineAccount));
                self.login.ely = false;
                self.login.choosing = false;
                self.login.visible = true;
            }
            Some(Action::SavedAccounts) => {
                self.login.providers = false;
                self.login.refresh_status();
                self.login.choosing = true;
                self.login.ely = false;
            }
            Some(Action::SavedPage(next)) => {
                if next && self.login.account_offset + 3 < self.login.saved_accounts.len() {
                    self.login.account_offset += 3;
                } else if !next {
                    self.login.account_offset = self.login.account_offset.saturating_sub(3);
                }
            }
            Some(Action::SelectAccount(index)) => {
                let mut accounts = crate::account::Accounts::load();
                if let Some(account) = self.login.saved_accounts.get(index)
                    && accounts.accounts.iter().any(|a| a.key() == account.key())
                {
                    accounts.selected = Some(account.key());
                    accounts.save()?;
                }
                self.session_ready = false;
                self.prepared_session = None;
                self.session_worker = None;
                self.login.refresh_status();
                self.refresh_wardrobe_account();
            }
            Some(Action::SelectOffline) => {
                self.catalog.config.save(&self.home)?;
                self.catalog.editing = None;
                crate::platform::hide();
                self.login.hide();
                let mut accounts = crate::account::Accounts::load();
                accounts.selected = None;
                accounts.save()?;
                self.session_ready = false;
                self.prepared_session = None;
                self.session_worker = None;
                self.login.choosing = false;
                self.login.refresh_status();
                self.refresh_wardrobe_account();
            }
            Some(Action::ElySignIn) => {
                self.login.providers = false;
                self.login.close();
                self.login.ely = true;
                self.login.visible = true;
            }
            Some(Action::SubmitEly) => {
                self.catalog.editing = None;
                crate::platform::hide();
                self.session_ready = false;
                self.prepared_session = None;
                self.session_worker = None;
                if !self.login.pending() {
                    self.login.start_ely();
                }
            }
            Some(Action::CloseLogin) => {
                self.login.hide();
                crate::platform::hide();
                self.catalog.editing = None;
                self.refresh_wardrobe_account();
            }
            Some(Action::DeviceSignIn) => {
                self.session_ready = false;
                self.prepared_session = None;
                self.session_worker = None;
                self.login
                    .start_device(&self.catalog.config.microsoft_client_id);
            }
            Some(Action::CopyLoginCode) => self.login.copy_code(),
            Some(Action::CancelLogin) => {
                self.login.close();
                self.login.visible = true;
            }
            Some(Action::ReviewApp) => self.login.review_app(),
            Some(Action::SignIn) => {
                self.session_ready = false;
                self.prepared_session = None;
                self.session_worker = None;
                self.login.start(&self.catalog.config.microsoft_client_id);
            }
            Some(Action::SignOut) => {
                self.session_ready = false;
                self.prepared_session = None;
                self.session_worker = None;
                self.login.sign_out();
                self.refresh_wardrobe_account();
            }
            Some(Action::LoginLink) => self.login.open_link(),
            Some(Action::Page(page)) => {
                if page == Page::Skins {
                    self.wardrobe.open(
                        &self.catalog.config.name,
                        &self.catalog.config.microsoft_client_id,
                    );
                }
                self.catalog.editing = None;
                self.catalog.project_modal = None;
                self.catalog.modal_worker = None;
                if crate::platform::active() {
                    crate::platform::hide();
                }
                if page == Page::Settings && self.catalog.page == Page::Settings {
                    self.catalog.settings_category = SettingsCategory::Overview;
                }
                self.catalog.page = page;
                self.catalog.scroll = 0.0;
                self.error = None;
                self.catalog.offset = 0;
                self.catalog.browse(&self.home);
            }
            Some(Action::Play) => self.action(events)?,
            Some(Action::WardrobeKind(cape)) => {
                self.wardrobe.set_tab(cape);
                self.catalog.scroll = 0.0;
            }
            Some(Action::WardrobeSelect(index)) => self.wardrobe.select(index),
            Some(Action::WardrobeImport) => self.wardrobe.import(),
            Some(Action::WardrobeCloud) => self
                .wardrobe
                .cloud(&self.catalog.config.microsoft_client_id, None),
            Some(Action::WardrobeUpload) => self.wardrobe.cloud(
                &self.catalog.config.microsoft_client_id,
                Some(wardrobe::CloudAction::Upload),
            ),
            Some(Action::WardrobeActivateCape(index)) => self.wardrobe.cloud(
                &self.catalog.config.microsoft_client_id,
                Some(wardrobe::CloudAction::Cape(index)),
            ),
            Some(Action::WardrobeUseCloud) => self.wardrobe.use_cloud(),
            Some(Action::WardrobeModel) => self.wardrobe.model(),
            Some(Action::WardrobeRotate(direction)) => {
                self.wardrobe.camera.rotate(-direction as f32 * 25.0, 0.0);
                self.wardrobe.preview();
            }
            Some(Action::WardrobeZoom(inward)) => {
                self.wardrobe
                    .camera
                    .zoom(if inward { 1.15 } else { 1.0 / 1.15 });
                self.wardrobe.preview();
            }
            Some(Action::WardrobeHideCape) => self.wardrobe.hide_cape(),
            Some(Action::Search) => {
                crate::platform::hide();
                self.catalog.offset = 0;
                self.catalog.search(&self.home);
            }
            Some(Action::FinishNameEdit) => {
                if self.catalog.editing == Some("name") {
                    self.refresh_wardrobe_account();
                }
                self.catalog.editing = None;
                crate::platform::hide();
                self.catalog.config.touch_name_changed();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::Field(name)) => {
                if let Some(value) = self.login.field(name) {
                    self.catalog.editing = Some(name);
                    if name == "ely_password" || name == "ely_otp" {
                        crate::platform::show_secret(value);
                    } else {
                        crate::platform::show(value);
                    }
                    return Ok(());
                }
                if name == "render_scale" {
                    self.catalog.render_scale_text =
                        self.catalog.config.render_scale.percent().to_string();
                    self.chooser = None;
                }
                self.catalog.editing = Some(name);
                let initial = match name {
                    "name" => &self.catalog.config.name,
                    "profile_name" => &self.catalog.config.custom_profile_name,
                    "profile_desc" => &self.catalog.config.custom_profile_desc,
                    "server" => &self.catalog.config.server,
                    "render_scale" => &self.catalog.render_scale_text,
                    "client_id" => &self.catalog.config.microsoft_client_id,
                    "theme_bg" => &self.catalog.config.theme.custom_bg_hex,
                    "theme_surface" => &self.catalog.config.theme.custom_surface_hex,
                    "theme_accent" => &self.catalog.config.theme.custom_accent_hex,
                    "theme_grad_start" => &self.catalog.config.theme.gradient_start_hex,
                    "theme_grad_mid" => &self.catalog.config.theme.gradient_mid_hex,
                    "theme_grad_end" => &self.catalog.config.theme.gradient_end_hex,
                    "installed_search" => &self.catalog.installed_query,
                    "settings_search" => &self.catalog.settings_query,
                    "fm_rename" | "fm_new_folder" => &self.catalog.fm_input_text,
                    _ => &self.catalog.query,
                };
                crate::platform::show(initial);
            }
            Some(Action::Install(i)) => self.catalog.install(&self.home, i),
            Some(Action::Next) => {
                self.catalog.offset += 12;
                self.catalog.search(&self.home);
            }
            Some(Action::Previous) => {
                self.catalog.offset = self.catalog.offset.saturating_sub(12);
                self.catalog.search(&self.home);
            }
            Some(Action::Profile) => self.open_chooser(chooser::Kind::Profile),
            Some(Action::Renderer) => self.open_chooser(chooser::Kind::Renderer),
            Some(Action::RenderScale) => self.open_chooser(chooser::Kind::RenderScale),
            Some(Action::ChooseRenderScale(scale)) => {
                self.catalog.config.render_scale = scale;
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
                crate::display::set_scale(scale);
            }
            Some(Action::Orientation) => self.open_chooser(chooser::Kind::Orientation),
            Some(Action::InputHost) => self.open_chooser(chooser::Kind::InputHost),
            Some(Action::ChooseInputHost(mode)) => {
                self.catalog.config.input_host = mode;
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
                crate::configure_input_mode(mode);
            }
            Some(Action::ChooseOrientation(mode)) => {
                self.catalog.config.orientation = mode;
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
                crate::platform::set_orientation(mode);
            }
            Some(Action::ChooseRenderer(mode)) => {
                self.catalog.config.renderer = mode;
                self.catalog.config.save(&self.home)?;
                self.chooser = None;
            }
            Some(Action::ChooseProfile(profile)) => {
                std::fs::create_dir_all(if profile == 0 {
                    self.home.clone()
                } else {
                    self.home
                        .join("profiles")
                        .join(format!("profile-{profile}"))
                })?;
                self.catalog.config.profile = profile;
                self.catalog.config.save(&self.home)?;
                self.catalog.refresh(&self.home);
                self.chooser = None;
            }
            Some(Action::Dismiss) => {
                self.exit_confirmation = false;
                self.chooser = None;
                if self.session_worker.is_none() {
                    self.session_ready = false;
                    self.prepared_session = None;
                }
            }
            Some(Action::Logs) => {
                self.catalog.status = crate::diagnostics::summary(&self.home);
                if let Ok(error) = std::fs::read_to_string(self.home.join("android-error.txt")) {
                    self.catalog.status = format!(
                        "Last handled error: {}. {}",
                        crate::diagnostics::redact(&error),
                        self.catalog.status
                    );
                }
                self.diagnostics_modal_open = true;
            }
            Some(Action::CloseDiagnosticsSummary) => {
                self.diagnostics_modal_open = false;
            }
            Some(Action::OpenReportsFolder) => {
                self.diagnostics_modal_open = false;
                self.catalog.status = if crate::platform::open_documents("reports") {
                    "Opened Reports folder in system Files.".into()
                } else {
                    "Reports are saved under Horizon Android > Reports.".into()
                };
            }
            Some(Action::ToggleLauncherAnimations) => {
                self.catalog.config.disable_launcher_animations =
                    !self.catalog.config.disable_launcher_animations;
                horizon_client::ui_theme::motion::set_launcher_animations_disabled(
                    self.catalog.config.disable_launcher_animations,
                );
                self.catalog.config.save(&self.home)?;
                self.catalog.status = if self.catalog.config.disable_launcher_animations {
                    "Launcher animations disabled (instant page & modal transitions)."
                } else {
                    "Launcher animations enabled."
                }
                .into();
            }
            Some(Action::ToggleGameAnimations) => {
                let next = !self.catalog.config.disable_game_animations;
                self.catalog.config.disable_game_animations = next;
                horizon_client::ui_theme::motion::set_game_animations_disabled(next);
                self.catalog.config.save(&self.home)?;
                self.catalog.config.apply_experimental_to_game(&self.home);
                self.modify_touch_settings(|t| t.disable_game_animations = next);
                self.catalog.status = if next {
                    "In-game animations disabled (static textures, instant FOV/menus, no view bobbing)."
                } else {
                    "In-game animations enabled."
                }
                .into();
            }
            Some(Action::ExportDiagnostics) if self.diagnostic_export.is_none() => {
                let home = self.home.clone();
                let (sender, receiver) = mpsc::channel();
                self.diagnostic_export = Some(receiver);
                self.catalog.status =
                    "Preparing local diagnostic ZIP. No upload; review before sharing.".into();
                std::thread::spawn(move || {
                    let result = crate::diagnostics::export(&home).map(|p| {
                        p.file_name()
                            .unwrap_or_default()
                            .to_string_lossy()
                            .into_owned()
                    });
                    let _ = sender.send(result);
                });
            }
            Some(Action::ExportDiagnostics) => {}
            Some(Action::RecordPerformance) => {
                self.catalog.config.record_performance = !self.catalog.config.record_performance;
                if !self.catalog.config.record_performance {
                    crate::performance::stop();
                }
                self.catalog.config.save(&self.home)?;
                self.catalog.status = if self.catalog.config.record_performance {
                    "Recording starts on Play. Detailed timings add overhead. Return here to save the report."
                } else { "Performance recording off." }.into();
            }
            Some(Action::ExportPerformance) => {
                self.catalog.status = match crate::performance::export(&self.home) {
                    Ok(()) => "Reports opened in Files. Copy or share a report from there.".into(),
                    Err(e) => e,
                };
            }
            Some(Action::SelectThemePreset(preset)) => {
                self.catalog.config.theme.apply_preset(preset);
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
                self.catalog.status = format!("Applied '{}' theme.", preset.label());
            }
            Some(Action::SetThemeAppearance(appearance)) => {
                self.catalog.config.theme.appearance = appearance;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
                self.catalog.status = format!("Switched to {}.", appearance.label());
            }
            Some(Action::SetThemeBackgroundMode(mode)) => {
                self.catalog.config.theme.background_mode = mode;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
                self.catalog.status = format!("Background style set to {}.", mode.label());
            }
            Some(Action::SetGradientDirection(dir)) => {
                self.catalog.config.theme.gradient_direction = dir;
                self.catalog.config.theme.background_mode =
                    horizon_client::ui_theme::BackgroundMode::Gradient;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
                self.catalog.status = format!("Gradient direction set to {}.", dir.label());
            }
            Some(Action::ToggleGradientThreeStops) => {
                self.catalog.config.theme.use_three_stops =
                    !self.catalog.config.theme.use_three_stops;
                self.catalog.config.theme.background_mode =
                    horizon_client::ui_theme::BackgroundMode::Gradient;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::StepGradientMidPos(dir)) => {
                let cur = self.catalog.config.theme.gradient_mid_pos;
                let next = if dir >= 0 {
                    if cur < 0.35 {
                        0.50
                    } else if cur < 0.65 {
                        0.75
                    } else {
                        0.25
                    }
                } else if cur > 0.65 {
                    0.50
                } else {
                    0.25
                };
                self.catalog.config.theme.gradient_mid_pos = next;
                self.catalog.config.theme.background_mode =
                    horizon_client::ui_theme::BackgroundMode::Gradient;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::SelectAccentSwatch(rgb)) => {
                self.catalog.config.theme.custom_accent_hex =
                    horizon_client::ui_theme::format_hex_color(rgb);
                self.catalog.config.theme.preset = horizon_client::ui_theme::ThemePreset::Custom;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::SelectBgSwatch(rgb)) => {
                self.catalog.config.theme.custom_bg_hex =
                    horizon_client::ui_theme::format_hex_color(rgb);
                self.catalog.config.theme.preset = horizon_client::ui_theme::ThemePreset::Custom;
                self.catalog.config.theme.background_mode =
                    horizon_client::ui_theme::BackgroundMode::Solid;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::SelectGradStartSwatch(rgb)) => {
                self.catalog.config.theme.gradient_start_hex =
                    horizon_client::ui_theme::format_hex_color(rgb);
                self.catalog.config.theme.preset = horizon_client::ui_theme::ThemePreset::Custom;
                self.catalog.config.theme.background_mode =
                    horizon_client::ui_theme::BackgroundMode::Gradient;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::SelectGradEndSwatch(rgb)) => {
                self.catalog.config.theme.gradient_end_hex =
                    horizon_client::ui_theme::format_hex_color(rgb);
                self.catalog.config.theme.preset = horizon_client::ui_theme::ThemePreset::Custom;
                self.catalog.config.theme.background_mode =
                    horizon_client::ui_theme::BackgroundMode::Gradient;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::ResetTheme) => {
                self.catalog.config.theme =
                    horizon_client::ui_theme::LauncherThemeConfig::default();
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
                self.catalog.status = "Theme reset to Horizon default (Obsidian Violet).".into();
            }
            Some(Action::SelectAccentPalette(idx)) => {
                let (hex, p_id) = match idx {
                    1 => ("#F59E0B", 1), // Gold
                    2 => ("#10B981", 2), // Green
                    3 => ("#F43F5E", 3), // Red
                    4 => ("#A855F7", 4), // Violet
                    _ => ("#38BDF8", 0), // Cyan
                };
                self.catalog.config.theme.custom_accent_hex = hex.into();
                self.catalog.config.theme.palette_idx = p_id;
                self.catalog.config.theme.preset = horizon_client::ui_theme::ThemePreset::Custom;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::SelectBgStyle(style)) => {
                self.catalog.config.theme.bg_style = (*style).to_owned();
                self.catalog.config.theme.preset = horizon_client::ui_theme::ThemePreset::Custom;
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::GlassOpacityDelta(delta)) => {
                self.catalog.config.theme.glass_opacity =
                    (self.catalog.config.theme.glass_opacity + delta).clamp(0.40, 0.98);
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::GlassBlurDelta(delta)) => {
                self.catalog.config.theme.glass_blur =
                    (self.catalog.config.theme.glass_blur + delta).clamp(0.0, 32.0);
                horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                self.anim.trigger_theme_pulse();
                self.catalog.config.save(&self.home)?;
            }
            Some(Action::ToggleKeepAlive) => {
                self.catalog.config.keepalive_enabled = !self.catalog.config.keepalive_enabled;
                self.catalog.config.save(&self.home)?;
                self.catalog.status = if self.catalog.config.keepalive_enabled {
                    "Background keep-alive enabled.".into()
                } else {
                    "Background keep-alive disabled.".into()
                };
            }
            Some(Action::ResetCategory(cat)) => {
                match cat {
                    SettingsCategory::Appearance | SettingsCategory::Theme => {
                        self.catalog.config.theme =
                            horizon_client::ui_theme::LauncherThemeConfig::default();
                        self.catalog.config.ui_scale_percent = 100;
                        self.catalog.config.disable_launcher_animations = false;
                        self.catalog.config.disable_game_animations = false;
                        horizon_client::ui_theme::set_active_theme(
                            self.catalog.config.theme.resolve(),
                        );
                    }
                    SettingsCategory::EngineMemory | SettingsCategory::Video => {
                        self.catalog.config.renderer = crate::catalog::RendererMode::Vulkan;
                        self.catalog.config.render_scale = crate::display::RenderScale::new(100);
                        self.catalog.config.worker_limit = 0;
                        self.catalog.config.cache_budget_mib = 256;
                        self.modify_game_settings(|s| {
                            s.render_distance = 12;
                            s.simulation_distance = 10;
                            s.fov = 70.0;
                            s.max_fps = 260;
                            s.vsync = false;
                        });
                    }
                    SettingsCategory::DownloadsKeepAlive => {
                        self.catalog.config.keepalive_enabled = true;
                        self.catalog.config.svc_enabled = true;
                        self.catalog.config.svc_rnnoise = true;
                    }
                    SettingsCategory::Controls => {
                        let game_home = self.catalog.config.game_home(&self.home);
                        let touch_path = game_home.join("horizon-touch.json");
                        let default_touch = horizon_client::touch::Settings::default();
                        let _ = default_touch.save_to(&touch_path);
                    }
                    SettingsCategory::Storage => {
                        self.catalog.config.cache_budget_mib = 256;
                    }
                    SettingsCategory::Experimental => {
                        self.catalog.config.super_perf_mode = false;
                        self.catalog.config.battery_perf_mode = false;
                        self.catalog.config.max_fps_mode = false;
                        self.catalog.config.zero_latency_touch = true;
                        self.catalog.config.fast_chunk_upload = true;
                        self.catalog.config.experimental_leaf_culling = false;
                        self.catalog.config.svc_enabled = true;
                        self.catalog.config.svc_rnnoise = true;
                        self.catalog.config.apply_experimental_to_game(&self.home);
                    }
                    _ => {}
                }
                self.catalog.config.save(&self.home)?;
                self.catalog.status = format!("Reset {} to defaults.", cat.title());
            }
            Some(Action::ExportAllSettings) => {
                let backup_path = self.home.join("horizon-settings-backup.json");
                if let Ok(json) = serde_json::to_string_pretty(&self.catalog.config) {
                    if std::fs::write(&backup_path, json).is_ok() {
                        self.catalog.status =
                            "Settings exported to horizon-settings-backup.json".into();
                    } else {
                        self.catalog.status = "Failed to export settings file.".into();
                    }
                }
            }
            Some(Action::ImportAllSettings) => {
                let backup_path = self.home.join("horizon-settings-backup.json");
                if backup_path.is_file() {
                    if let Ok(data) = std::fs::read(&backup_path) {
                        if let Ok(cfg) = serde_json::from_slice::<crate::catalog::Config>(&data) {
                            self.catalog.config = cfg;
                            self.catalog.config.save(&self.home)?;
                            horizon_client::ui_theme::set_active_theme(
                                self.catalog.config.theme.resolve(),
                            );
                            self.catalog.status = "Settings restored from backup.".into();
                        } else {
                            self.catalog.status = "Invalid backup file format.".into();
                        }
                    }
                } else {
                    let _ = crate::documents::begin_into_dir(self.home.clone());
                    self.catalog.status = "Select a settings JSON backup via File Picker.".into();
                }
            }
            Some(Action::ClearSettingsSearch) => {
                self.catalog.settings_query.clear();
            }
            Some(Action::ChooseImportBackground) => {
                let bg_dir = self.home.join("backgrounds");
                let _ = std::fs::create_dir_all(&bg_dir);
                if let Err(e) = crate::documents::begin_into_dir(bg_dir) {
                    self.catalog.status = e;
                } else {
                    self.catalog.config.theme.bg_style = "custom".into();
                    self.catalog.config.save(&self.home)?;
                    horizon_client::ui_theme::set_active_theme(self.catalog.config.theme.resolve());
                }
            }
            Some(Action::DismissStarlingNotice) => {
                self.catalog.starling_notice_dismissed = true;
            }
            Some(Action::SkipIntro) => {
                self.anim.intro_done = true;
            }
            Some(Action::SetInstalledSubTab(tab)) => {
                self.catalog.installed_sub_tab = tab;
                self.catalog.editing = None;
                crate::platform::hide();
                self.catalog.scroll = 0.0;
                if tab == crate::catalog::InstalledSubTab::FileManager {
                    self.catalog.refresh_fm(&self.home);
                }
                self.anim.trigger_page_transition();
            }
            Some(Action::SetInstalledFilter(filter)) => {
                self.catalog.installed_filter = filter;
                self.catalog.scroll = 0.0;
            }
            Some(Action::CycleInstalledSort) => {
                self.catalog.installed_sort = self.catalog.installed_sort.next();
                self.catalog.sort_local_items();
                self.catalog.scroll = 0.0;
            }
            Some(Action::ExportLocal(idx)) => {
                if let Some(item) = self.catalog.local.get(idx).cloned() {
                    let game_home = self.catalog.config.game_home(&self.home);
                    let direct = game_home.join(&item.file);
                    let path = if direct.exists() {
                        direct
                    } else {
                        game_home.join(item.kind.folder()).join(&item.file)
                    };
                    self.catalog.status = match crate::documents::export_file(
                        &self.home,
                        self.catalog.config.profile,
                        &path,
                    ) {
                        Ok(msg) => msg,
                        Err(e) => e,
                    };
                }
            }
            Some(Action::FmNavigateRoot(rel)) => {
                self.catalog.fm_navigate(&self.home, (*rel).to_owned());
            }
            Some(Action::FmBreadcrumb(depth)) => {
                if depth == 0 {
                    self.catalog.fm_navigate(&self.home, String::new());
                } else {
                    let target = self
                        .catalog
                        .fm_current_dir
                        .split('/')
                        .filter(|s| !s.is_empty())
                        .take(depth)
                        .collect::<Vec<_>>()
                        .join("/");
                    self.catalog.fm_navigate(&self.home, target);
                }
            }
            Some(Action::FmBack) => {
                let _ = self.catalog.fm_back(&self.home);
            }
            Some(Action::FmForward) => {
                let _ = self.catalog.fm_forward(&self.home);
            }
            Some(Action::FmUp) => {
                self.catalog.fm_up(&self.home);
            }
            Some(Action::FmOpenEntry(idx)) => {
                if let Some(entry) = self.catalog.fm_entries.get(idx).cloned() {
                    if entry.is_dir {
                        self.catalog.fm_navigate(&self.home, entry.rel_path);
                    } else {
                        let game_home = self.catalog.config.game_home(&self.home);
                        match crate::document_store::fm_details(
                            &self.home,
                            &game_home,
                            &entry.rel_path,
                        ) {
                            Ok(details) => {
                                self.catalog.fm_modal =
                                    Some(crate::catalog::FmModal::Details(details));
                            }
                            Err(e) => self.catalog.status = e,
                        }
                    }
                }
            }
            Some(Action::FmToggleSelect(idx)) => {
                if let Some(entry) = self.catalog.fm_entries.get(idx) {
                    let rel = entry.rel_path.clone();
                    if !self.catalog.fm_selected.remove(&rel) {
                        self.catalog.fm_selected.insert(rel);
                    }
                }
            }
            Some(Action::FmSelectAll) => {
                for entry in &self.catalog.fm_entries {
                    self.catalog.fm_selected.insert(entry.rel_path.clone());
                }
            }
            Some(Action::FmClearSelection) => {
                self.catalog.fm_selected.clear();
            }
            Some(Action::FmCopy) => {
                self.catalog.fm_copy_or_cut(&self.home, false);
            }
            Some(Action::FmCut) => {
                self.catalog.fm_copy_or_cut(&self.home, true);
            }
            Some(Action::FmPaste) => {
                self.catalog.fm_paste_here(&self.home);
            }
            Some(Action::FmImportHere) => {
                let game_home = self.catalog.config.game_home(&self.home);
                match crate::document_store::resolve_fm_path(
                    &self.home,
                    &game_home,
                    &self.catalog.fm_current_dir,
                ) {
                    Ok(target_dir) => {
                        if let Err(e) = crate::documents::begin_into_dir(target_dir) {
                            self.catalog.status = e;
                        }
                    }
                    Err(e) => self.catalog.status = e,
                }
            }
            Some(Action::FmOpenNewFolder) => {
                self.catalog.fm_input_text = "New Folder".into();
                self.catalog.fm_modal = Some(crate::catalog::FmModal::NewFolder);
                self.catalog.editing = Some("fm_new_folder");
                crate::platform::show(&self.catalog.fm_input_text);
            }
            Some(Action::FmSubmitNewFolder) => {
                self.catalog.fm_submit_new_folder(&self.home);
            }
            Some(Action::FmOpenRename(idx)) => {
                if let Some(entry) = self.catalog.fm_entries.get(idx).cloned() {
                    if crate::document_store::is_protected_root_folder(&entry.rel_path) {
                        self.catalog.status =
                            "Built-in profile root folders cannot be renamed.".into();
                    } else {
                        self.catalog.fm_input_text = entry.name;
                        self.catalog.fm_modal = Some(crate::catalog::FmModal::Rename {
                            target_rel: entry.rel_path,
                        });
                        self.catalog.editing = Some("fm_rename");
                        crate::platform::show(&self.catalog.fm_input_text);
                    }
                }
            }
            Some(Action::FmOpenRenameSelected) => {
                if let Some(rel) = self.catalog.fm_selected.iter().next().cloned() {
                    if crate::document_store::is_protected_root_folder(&rel) {
                        self.catalog.status =
                            "Built-in profile root folders cannot be renamed.".into();
                    } else {
                        let cur_name = rel.rsplit_once('/').map_or(rel.as_str(), |(_, n)| n);
                        self.catalog.fm_input_text = cur_name.to_owned();
                        self.catalog.fm_modal =
                            Some(crate::catalog::FmModal::Rename { target_rel: rel });
                        self.catalog.editing = Some("fm_rename");
                        crate::platform::show(&self.catalog.fm_input_text);
                    }
                } else {
                    self.catalog.status = "Select a single file or folder to rename.".into();
                }
            }
            Some(Action::FmSubmitRename) => {
                self.catalog.fm_submit_rename(&self.home);
            }
            Some(Action::FmRequestDeleteSingle(idx)) => {
                if let Some(entry) = self.catalog.fm_entries.get(idx).cloned() {
                    if crate::document_store::is_protected_root_folder(&entry.rel_path) {
                        self.catalog.status =
                            "Built-in profile root folders cannot be deleted.".into();
                    } else {
                        self.catalog.fm_modal = Some(crate::catalog::FmModal::ConfirmDelete {
                            paths: vec![entry.rel_path],
                        });
                    }
                }
            }
            Some(Action::FmRequestDeleteSelected) => {
                if self.catalog.fm_selected.is_empty() {
                    self.catalog.status = "Select one or more items to delete.".into();
                } else {
                    let paths: Vec<String> = self
                        .catalog
                        .fm_selected
                        .iter()
                        .filter(|p| !crate::document_store::is_protected_root_folder(p))
                        .cloned()
                        .collect();
                    if paths.is_empty() {
                        self.catalog.status =
                            "Built-in profile root folders cannot be deleted.".into();
                    } else {
                        self.catalog.fm_modal =
                            Some(crate::catalog::FmModal::ConfirmDelete { paths });
                    }
                }
            }
            Some(Action::FmConfirmDelete) => {
                self.catalog.fm_confirm_delete(&self.home);
            }
            Some(Action::FmOpenDetails(idx)) => {
                if let Some(entry) = self.catalog.fm_entries.get(idx).cloned() {
                    let game_home = self.catalog.config.game_home(&self.home);
                    match crate::document_store::fm_details(&self.home, &game_home, &entry.rel_path)
                    {
                        Ok(details) => {
                            self.catalog.fm_modal = Some(crate::catalog::FmModal::Details(details));
                        }
                        Err(e) => self.catalog.status = e,
                    }
                }
            }
            Some(Action::FmExportSingle(idx)) => {
                if let Some(entry) = self.catalog.fm_entries.get(idx).cloned() {
                    let game_home = self.catalog.config.game_home(&self.home);
                    match crate::document_store::resolve_fm_path(
                        &self.home,
                        &game_home,
                        &entry.rel_path,
                    ) {
                        Ok(path) => {
                            self.catalog.status = match crate::documents::export_file(
                                &self.home,
                                self.catalog.config.profile,
                                &path,
                            ) {
                                Ok(msg) => msg,
                                Err(e) => e,
                            };
                        }
                        Err(e) => self.catalog.status = e,
                    }
                }
            }
            Some(Action::FmExportSelected) => {
                if let Some(rel) = self.catalog.fm_selected.iter().next().cloned() {
                    let game_home = self.catalog.config.game_home(&self.home);
                    match crate::document_store::resolve_fm_path(&self.home, &game_home, &rel) {
                        Ok(path) => {
                            self.catalog.status = match crate::documents::export_file(
                                &self.home,
                                self.catalog.config.profile,
                                &path,
                            ) {
                                Ok(msg) => msg,
                                Err(e) => e,
                            };
                        }
                        Err(e) => self.catalog.status = e,
                    }
                } else {
                    self.catalog.status = "Select a file to export.".into();
                }
            }
            Some(Action::FmCloseModal) => {
                self.catalog.fm_modal = None;
                self.catalog.editing = None;
                crate::platform::hide();
            }
            None => {}
        }
        Ok(())
    }

    fn toggle_bundled_mod(&mut self, id: &'static str) -> Result<()> {
        let game = self.catalog.config.game_home(&self.home);
        let mut list = horizon_client::mod_list::ModList::load(&game);
        let enabled = list.enabled.contains(id) && !list.disabled.contains(id);
        if enabled {
            list.enabled.remove(id);
            list.disabled.insert(id.into());
        } else {
            list.disabled.remove(id);
            list.enabled.insert(id.into());
        }
        std::fs::create_dir_all(&game)?;
        std::fs::write(game.join(horizon_client::mod_list::FILE), list.serialize())?;
        self.catalog.refresh(&self.home);
        self.catalog.status = format!(
            "Bundled {id} {}. Applies on Play; settings are in Options > Mods.",
            if enabled {
                "disabled"
            } else {
                "installed & enabled"
            }
        );
        Ok(())
    }

    fn modify_game_settings(&mut self, f: impl FnOnce(&mut horizon_client::settings::Settings)) {
        let game_home = self.catalog.config.game_home(&self.home);
        let _ = std::fs::create_dir_all(&game_home);
        let path = game_home.join("options.txt");
        let mut s = horizon_client::settings::Settings::load(&path);
        f(&mut s);
        s.save(&path);
    }

    fn modify_touch_settings(&mut self, f: impl FnOnce(&mut horizon_client::touch::Settings)) {
        let game_home = self.catalog.config.game_home(&self.home);
        let _ = std::fs::create_dir_all(&game_home);
        let mut t =
            horizon_client::touch::Settings::load_from(&game_home.join("horizon-touch.json"));
        f(&mut t);
        if !t.is_default_preset() {
            let _ = t.save_replace_preset(&game_home);
        } else {
            let _ = t.save_to(&game_home.join("horizon-touch.json"));
        }
    }

    fn apply_slider_fraction(&mut self, action: Action, frac: f32) {
        let t = frac.clamp(0.0, 1.0);
        match action {
            Action::RenderScaleDelta(_) => {
                let next_pct = (25.0 + t * 75.0).round() as u32;
                let scale = crate::display::RenderScale::new(next_pct);
                self.catalog.config.render_scale = scale;
                let _ = self.catalog.config.save(&self.home);
                crate::display::set_scale(scale);
                self.modify_game_settings(|s| {
                    s.horizon.resolution_scale = next_pct;
                });
                self.modify_touch_settings(|ts| {
                    ts.resolution_scale_percent = next_pct;
                });
            }
            Action::RenderDistDelta(_) => {
                let rd = (2.0 + t * 30.0).round() as i32;
                self.modify_game_settings(|s| s.render_distance = rd.clamp(2, 32));
            }
            Action::SimDistDelta(_) => {
                let sd = (5.0 + t * 27.0).round() as i32;
                self.modify_game_settings(|s| s.simulation_distance = sd.clamp(5, 32));
            }
            Action::FovDelta(_) => {
                let fov = (30.0 + t * 80.0).round();
                self.modify_game_settings(|s| s.fov = fov.clamp(30.0, 110.0));
            }
            Action::TouchSensDelta(_) => {
                let v = ((0.10 + t * 2.90) * 100.0).round() / 100.0;
                self.modify_touch_settings(|ts| ts.sensitivity = v.clamp(0.10, 3.0));
            }
            Action::MouseSensDelta(_) => {
                let v = ((0.05 + t * 0.95) * 100.0).round() / 100.0;
                self.modify_game_settings(|s| s.sensitivity = v.clamp(0.05, 1.0));
            }
            Action::ButtonScaleDelta(_) => {
                let v = ((0.60 + t * 1.00) * 100.0).round() / 100.0;
                self.modify_touch_settings(|ts| ts.button_scale = v.clamp(0.60, 1.60));
            }
            Action::PointerSizeDelta(_) => {
                let v = ((0.50 + t * 1.50) * 100.0).round() / 100.0;
                self.modify_touch_settings(|ts| {
                    ts.virtual_mouse_pointer_scale = v.clamp(0.50, 2.0)
                });
            }
            Action::GyroSensDelta(_) => {
                let v = ((0.10 + t * 2.90) * 100.0).round() / 100.0;
                self.modify_touch_settings(|ts| ts.gyro_sensitivity = v.clamp(0.10, 3.0));
            }
            _ => {}
        }
    }

    pub(crate) fn ui_density(&self) -> f32 {
        let (Some(r), Some(gui)) = (&self.renderer, &self.gui) else {
            return 1.0;
        };
        let mut batch = HudBatch::new([r.config.width, r.config.height]);
        Ui::mobile(&gui.atlas, &mut batch, (0.0, 0.0)).density
    }

    pub(crate) fn touch_event(&mut self, events: &ActiveEventLoop, t: Touch) -> Result<()> {
        if self.catalog.page == Page::Skins
            && !self.login.visible
            && self.wardrobe.gesture(&t, self.ui_density())
        {
            if let Some(window) = &self.window {
                window.request_redraw();
            }
            return Ok(());
        }
        self.focus = None;
        self.pending_focus = None;
        if let Some(window) = &self.window {
            window.request_redraw();
        }
        self.cursor = (t.location.x, t.location.y);

        // Route all touches in the MCPE + GoyControl Editor through touch_hud_editor
        // and persist changes directly to the active profile's game_home.
        if self.control_editor.is_some() {
            let (rw, rh) = self.renderer.as_ref().map_or((1920, 1080), |r| {
                (r.config.width.max(1), r.config.height.max(1))
            });
            let game_home = self.catalog.config.game_home(&self.home);
            let prev_cwd = std::env::current_dir().ok();
            let _ = std::env::set_current_dir(&game_home);
            if let (Some(tc), Some(gui)) = (&mut self.control_editor, &self.gui) {
                tc.viewport(rw, rh);
                let mut b = HudBatch::new([rw, rh]);
                let norm = [
                    (t.location.x as f32 / rw as f32).clamp(0.0, 1.0),
                    (t.location.y as f32 / rh as f32).clamp(0.0, 1.0),
                ];
                let ended = matches!(t.phase, TouchPhase::Ended | TouchPhase::Cancelled);
                let closed = horizon_client::touch_ui::touch_hud_editor(
                    tc,
                    &mut b,
                    &gui.atlas,
                    t.id,
                    norm,
                    t.phase == TouchPhase::Started,
                    ended,
                );
                if let Some(ed) = &mut tc.editor
                    && std::mem::take(&mut ed.keyboard_request)
                {
                    let text = if ed.editing_save_desc {
                        ed.save_desc_input.clone()
                    } else {
                        ed.save_name_input.clone()
                    };
                    crate::platform::show(&text);
                }
                if ended || closed {
                    let _ = tc.settings.save_to(&game_home.join("horizon-touch.json"));
                }
                if closed || tc.editor.is_none() {
                    self.catalog.status = format!(
                        "Saved {} preset '{}' (controls/{}/{}.json).",
                        tc.settings.control_style.label(),
                        tc.settings.preset_name,
                        tc.settings.control_style.storage_subdir(),
                        tc.settings.preset_id
                    );
                    self.control_editor = None;
                }
            }
            if let Some(prev) = prev_cwd {
                let _ = std::env::set_current_dir(prev);
            }
            return Ok(());
        }

        let density = self.ui_density();
        let px = t.location.x as f32 / density;
        let py = t.location.y as f32 / density;
        match t.phase {
            TouchPhase::Started => {
                let hit_slider = SLIDER_TRACK_TARGETS.with(|list| {
                    list.borrow()
                        .iter()
                        .rev()
                        .find(|([x, y, w, h], _, _, _)| {
                            (*x..*x + *w).contains(&px) && (*y..*y + *h).contains(&py)
                        })
                        .map(|(_, action, bar_x, bar_w)| (*action, *bar_x, *bar_w))
                });
                if let Some((action, bar_x, bar_w)) = hit_slider {
                    ACTIVE_SLIDER_DRAG.with(|c| c.set(Some((action, bar_x, bar_w))));
                    self.drag = None;
                    let frac = ((px - bar_x) / bar_w.max(1.0)).clamp(0.0, 1.0);
                    self.apply_slider_fraction(action, frac);
                } else {
                    ACTIVE_SLIDER_DRAG.with(|c| c.set(None));
                    self.drag = Some(super::gesture::Drag::new(t.location.x, t.location.y));
                }
            }
            TouchPhase::Moved => {
                if let Some((action, bar_x, bar_w)) = ACTIVE_SLIDER_DRAG.with(|c| c.get()) {
                    let frac = ((px - bar_x) / bar_w.max(1.0)).clamp(0.0, 1.0);
                    self.apply_slider_fraction(action, frac);
                } else {
                    let density_f64 = density as f64;
                    if let Some(drag) = &mut self.drag {
                        let delta = drag.update(t.location.x, t.location.y, density_f64);
                        if drag.moved {
                            if let Some(chooser) = &mut self.chooser {
                                chooser.scroll = (chooser.scroll + delta).max(0.0);
                            } else if let Some(modal) = &mut self.catalog.project_modal {
                                modal.scroll = (modal.scroll + delta).max(0.0);
                            } else if self
                                .list_view
                                .is_some_and(|view| drag.origin_y() / density_f64 >= view[0] as f64)
                            {
                                self.catalog.scroll = (self.catalog.scroll + delta).max(0.0);
                            }
                        }
                    }
                }
            }
            TouchPhase::Ended => {
                if ACTIVE_SLIDER_DRAG.with(|c| c.take()).is_some() {
                    self.drag = None;
                } else if self.drag.take().is_some_and(|drag| !drag.moved) {
                    self.activate(events)?;
                }
            }
            TouchPhase::Cancelled => {
                ACTIVE_SLIDER_DRAG.with(|c| c.set(None));
                self.drag = None;
            }
        }
        Ok(())
    }
    pub(crate) fn type_key(&mut self, key: KeyEvent) {
        if key.physical_key == PhysicalKey::Code(KeyCode::Escape)
            || key.logical_key
                == winit::keyboard::Key::Named(winit::keyboard::NamedKey::BrowserBack)
        {
            if !key.repeat {
                self.request_back();
            }
            return;
        }
        let editor_typing = self
            .control_editor
            .as_ref()
            .and_then(|tc| tc.editor.as_ref())
            .is_some_and(|ed| ed.save_dialog_open);
        if self.catalog.editing.is_none() && !editor_typing {
            return;
        }
        let out = match key.physical_key {
            PhysicalKey::Code(KeyCode::Backspace) => Some(Output::Backspace),
            PhysicalKey::Code(KeyCode::Enter) => {
                crate::platform::hide();
                Some(Output::Enter)
            }
            _ => key.text.map(|s| Output::Text(s.into())),
        };
        if let Some(out) = out {
            self.edit_text(out);
        }
    }
}
