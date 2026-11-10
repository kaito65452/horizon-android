use super::*;
use horizon_client::ui_theme::{Face, Icon, Ui, color};

pub fn draw_browse_view(
    launcher: &mut Launcher,
    ui: &mut Ui,
    content_x: f32,
    content_w: f32,
    h: f32,
    slide_y: f32,
) {
    let page = launcher.catalog.page;

    let header_y = 62.0 + slide_y;
    // Separated store browsing categories: Mods, Resource Packs, Shaders
    let store_cats: [(Page, &'static str); 3] = [
        (Page::Mods, "Mods"),
        (Page::Packs, "Resource Packs"),
        (Page::Shaders, "Shaders"),
    ];
    let sub_tab_w = 110.0;
    let sub_tab_gap = 6.0;
    for (i, (cat_page, label)) in store_cats.into_iter().enumerate() {
        let bx = content_x + i as f32 * (sub_tab_w + sub_tab_gap);
        let by = header_y - 2.0;
        let active = page == cat_page;
        button(
            &mut ui,
            &mut launcher.targets,
            [bx, by, sub_tab_w, 30.0],
            label,
            Action::Page(cat_page),
            active,
        );
    }
    if matches!(page, Page::Shaders | Page::Mods) {
        use crate::shader_catalog::Origin;
        let seg_w = 90.0;
        for (i, origin) in [Origin::Horizon, Origin::Modrinth].into_iter().enumerate() {
            let rx = content_x + content_w - (2 - i) as f32 * (seg_w + 6.0) + 6.0;
            let rect = [rx, header_y - 2.0, seg_w, 30.0];
            let active = (if page == Page::Mods {
                launcher.catalog.config.mod_source
            } else {
                launcher.catalog.config.shader_source
            }) == origin;
            button(
                &mut ui,
                &mut launcher.targets,
                rect,
                origin.source().label(),
                if page == Page::Mods {
                    Action::ModSource(origin)
                } else {
                    Action::ShaderSource(origin)
                },
                active,
            );
        }
    }

    // Search bar + Search button + Open folder / Import file pills
    let search_y = 98.0 + slide_y;
    let search_btn_w = 98.0;
    let field_w = (content_w - search_btn_w - 8.0).max(120.0);
    let search_hint = if page == Page::Mods {
        "Search mods or paste .hmod HTTPS link..."
    } else {
        "Search catalog..."
    };
    let search_rect = ui.text_field(
        content_x,
        search_y,
        field_w,
        36.0,
        "",
        &launcher.catalog.query,
        search_hint,
        launcher.catalog.editing == Some("search"),
    );
    launcher
        .targets
        .push((search_rect, Action::Field("search")));

    button(
        &mut ui,
        &mut launcher.targets,
        [content_x + field_w + 8.0, search_y, search_btn_w, 36.0],
        if page == Page::Mods && launcher.catalog.query.trim().starts_with("https://") {
            "Import .hmod"
        } else {
            "Search"
        },
        Action::Search,
        true,
    );

    let kind = match page {
        Page::Mods => horizon_client::content_files::Kind::Mods,
        Page::Shaders => horizon_client::content_files::Kind::Shaders,
        _ => horizon_client::content_files::Kind::Packs,
    };
    let sub_y = 140.0 + slide_y;
    button(
        &mut ui,
        &mut launcher.targets,
        [content_x, sub_y, 114.0, 29.0],
        "Open folder",
        Action::OpenFolder(kind),
        false,
    );
    button(
        &mut ui,
        &mut launcher.targets,
        [content_x + 122.0, sub_y, 114.0, 29.0],
        "Import file",
        Action::Import(kind),
        false,
    );
    if !compact {
        let status_line = launcher
            .error
            .as_deref()
            .unwrap_or(&launcher.catalog.status);
        let fitted_status = ui.fit_text(
            Face::Regular,
            12.5,
            status_line,
            (content_w - 248.0).max(60.0),
        );
        ui.text(
            Face::Regular,
            12.5,
            content_x + 246.0,
            sub_y + 5.0,
            blaze_muted(),
            &fitted_status,
        );
    }

    let bundled: Vec<_> = if page == Page::Mods
        && launcher.catalog.config.mod_source == crate::shader_catalog::Origin::Horizon
    {
        let query = launcher.catalog.query.to_lowercase();
        horizon_client::mod_list::ANDROID_BUNDLED
            .into_iter()
            .filter(|(id, title, _)| id.contains(&query) || title.to_lowercase().contains(&query))
            .collect()
    } else {
        Vec::new()
    };
    let catalog_rows: Vec<_> = self
        .catalog
        .results
        .iter()
        .enumerate()
        .filter(|(_, p)| {
            !(page == Page::Mods
                && p.source == crate::browser::SourceId::Horizon
                && horizon_client::mod_list::ANDROID_BUNDLED
                    .iter()
                    .any(|(id, _, _)| *id == p.id))
        })
        .collect();
    let row_h = 72.0;
    let row_count = bundled.len() + catalog_rows.len();
    let top = 176.0 + slide_y;
    let bottom = h - 46.0;
    launcher.catalog.scroll = self
        .catalog
        .scroll
        .clamp(0.0, (row_count as f32 * row_h - (bottom - top)).max(0.0));
    launcher.list_view = Some([
        top,
        bottom,
        (row_count as f32 * row_h - (bottom - top)).max(0.0),
    ]);

    let fallback_icon = page_icon(page);
    ui.set_clip(Some([
        content_x - 2.0,
        top,
        content_w + 4.0,
        (bottom - top).max(1.0),
    ]));

    for (row, (id, title, description)) in bundled.iter().enumerate() {
        let y = top + row as f32 * row_h - launcher.catalog.scroll;
        let enabled = launcher.catalog.bundled_enabled.contains(*id);
        let card_rect = [content_x, y, content_w - 118.0, 64.0];
        let btn_rect = [content_x + content_w - 112.0, y + 13.0, 100.0, 38.0];
        launcher
            .list_targets
            .push((card_rect, Action::OpenBundledProject(id)));
        launcher
            .list_targets
            .push((btn_rect, Action::ToggleBundled(id)));
        if y + 64.0 < top || y > bottom {
            continue;
        }
        ui.rounded(content_x, y, content_w, 64.0, 14.0, blaze_card_bg());
        ui.border(content_x, y, content_w, 64.0, 14.0, blaze_card_border());

        // Project icon: fetched Modrinth PFP if available, else MY3 vector icon badge.
        let drawn_icon = if let Some((iw, ih, rgba)) = launcher.catalog.icons.get(*id) {
            ui.image(id, *iw, *ih, rgba, content_x + 10.0, y + 10.0, 44.0, 44.0)
        } else {
            false
        };
        if !drawn_icon {
            ui.rounded(
                content_x + 10.0,
                y + 10.0,
                44.0,
                44.0,
                12.0,
                blaze_accent_soft(),
            );
            ui.icon(
                fallback_icon,
                content_x + 21.0,
                y + 21.0,
                22.0,
                blaze_accent(),
            );
        }

        // Badge chip ("Built-in • Tap for info")
        let text_max = (content_w - 190.0).max(80.0);
        let fitted_title = ui.fit_text(Face::SemiBold, 15.0, title, text_max);
        let rich_desc = self
            .catalog
            .enriched_summaries
            .get(*id)
            .map_or(*description, String::as_str);
        let summary = ui.fit_text(Face::Regular, 12.5, rich_desc, text_max);
        ui.text(
            Face::SemiBold,
            15.0,
            content_x + 64.0,
            y + 8.0,
            blaze_text(),
            &fitted_title,
        );
        ui.text(
            Face::Regular,
            12.5,
            content_x + 64.0,
            y + 28.0,
            blaze_muted(),
            &summary,
        );
        ui.text(
            Face::Medium,
            11.0,
            content_x + 64.0,
            y + 45.0,
            blaze_accent(),
            "Horizon Built-in • Tap for versions, Modrinth info & screenshots",
        );
        if let Some(c) = ui.clip_rect(card_rect) {
            launcher.targets.push((c, Action::OpenBundledProject(id)));
        }
        button(
            &mut ui,
            &mut launcher.targets,
            btn_rect,
            if enabled { "Installed" } else { "Install" },
            Action::ToggleBundled(id),
            !enabled,
        );
    }

    for (row, (i, p)) in catalog_rows.into_iter().enumerate() {
        let y = top + (row + bundled.len()) as f32 * row_h - launcher.catalog.scroll;
        let download = launcher.catalog.download_label(p);
        let card_rect = [content_x, y, content_w - 118.0, 64.0];
        let btn_rect = [content_x + content_w - 112.0, y + 13.0, 100.0, 38.0];
        launcher
            .list_targets
            .push((card_rect, Action::OpenProject(i)));
        if download.is_none() {
            launcher.list_targets.push((btn_rect, Action::Install(i)));
        }
        if y + 64.0 < top || y > bottom {
            continue;
        }
        ui.rounded(content_x, y, content_w, 64.0, 14.0, blaze_card_bg());
        ui.border(content_x, y, content_w, 64.0, 14.0, blaze_card_border());

        // Project icon: fetched RGBA icon if ready, else MY3 vector icon badge.
        let drawn_icon = if let Some((iw, ih, rgba)) = launcher.catalog.icons.get(&p.id) {
            ui.image(
                &p.id,
                *iw,
                *ih,
                rgba,
                content_x + 10.0,
                y + 10.0,
                44.0,
                44.0,
            )
        } else {
            false
        };
        if !drawn_icon {
            ui.rounded(
                content_x + 10.0,
                y + 10.0,
                44.0,
                44.0,
                12.0,
                blaze_accent_soft(),
            );
            ui.icon(
                fallback_icon,
                content_x + 21.0,
                y + 21.0,
                22.0,
                blaze_accent(),
            );
        }

        let text_max = (content_w - 190.0).max(80.0);
        let title = ui.fit_text(Face::SemiBold, 15.0, &p.title, text_max);
        let rich_summary = if crate::catalog::source_only_mod(p) {
            "Native .hnmod temporarily unsupported on Android; built-in mods are available."
        } else {
            launcher
                .catalog
                .enriched_summaries
                .get(&p.id)
                .map_or(p.summary.as_str(), String::as_str)
        };
        let summary = ui.fit_text(Face::Regular, 12.5, rich_summary, text_max);
        ui.text(
            Face::SemiBold,
            15.0,
            content_x + 64.0,
            y + 8.0,
            blaze_text(),
            &title,
        );
        ui.text(
            Face::Regular,
            12.5,
            content_x + 64.0,
            y + 28.0,
            blaze_muted(),
            &summary,
        );
        let meta_tag = if p.downloads > 0 {
            format!(
                "{} • {} downloads • Tap for versions & screenshots",
                p.source.label(),
                format_downloads(p.downloads)
            )
        } else {
            format!(
                "{} • Tap for versions, description & screenshots",
                p.source.label()
            )
        };
        let fitted_meta = ui.fit_text(Face::Medium, 11.0, &meta_tag, text_max);
        ui.text(
            Face::Medium,
            11.0,
            content_x + 64.0,
            y + 45.0,
            blaze_accent(),
            &fitted_meta,
        );
        if let Some(c) = ui.clip_rect(card_rect) {
            launcher.targets.push((c, Action::OpenProject(i)));
        }

        if let Some(state) = download {
            ui.rounded(
                btn_rect[0],
                btn_rect[1],
                btn_rect[2],
                btn_rect[3],
                19.0,
                blaze_surface_high(),
            );
            ui.icon(
                Icon::Check,
                btn_rect[0] + 10.0,
                btn_rect[1] + 11.0,
                15.0,
                blaze_accent(),
            );
            ui.text(
                Face::Medium,
                13.0,
                btn_rect[0] + 28.0,
                btn_rect[1] + 9.0,
                blaze_text(),
                &state,
            );
            if let Some(c) = ui.clip_rect(btn_rect) {
                launcher.targets.push((c, Action::OpenProject(i)));
            }
        } else {
            button(
                &mut ui,
                &mut launcher.targets,
                btn_rect,
                launcher.catalog.install_button(p),
                Action::Install(i),
                true,
            );
        }
    }

    ui.set_clip(None);
    let pager_y = h - 42.0;
    button(
        &mut ui,
        &mut launcher.targets,
        [content_x, pager_y, 102.0, 32.0],
        "Previous",
        Action::Previous,
        false,
    );
    button(
        &mut ui,
        &mut launcher.targets,
        [content_x + 110.0, pager_y, 102.0, 32.0],
        "Next",
        Action::Next,
        false,
    );
}
