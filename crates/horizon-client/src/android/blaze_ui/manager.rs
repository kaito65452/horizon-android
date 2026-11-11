use super::*;
use horizon_client::ui_theme::{Face, Icon, Ui};

impl Launcher {
    fn draw_installed(
        &mut self,
        ui: &mut Ui,
        content_x: f32,
        content_w: f32,
        h: f32,
        compact: bool,
        slide_y: f32,
    ) {
        use crate::catalog::{InstalledFilter, InstalledSubTab};
        use horizon_client::content_files::Kind;

        let header_y = 60.0 + slide_y;
        ui.text(
            Face::SemiBold,
            19.0,
            content_x,
            header_y + 2.0,
            blaze_text(),
            if self.catalog.installed_sub_tab == InstalledSubTab::Library {
                "Installed"
            } else {
                "Profile File Manager"
            },
        );

        // Top right segmented sub-tab switcher (Library vs File Manager)
        let tab_w = if compact { 104.0 } else { 126.0 };
        let lib_rect = [
            content_x + content_w - tab_w * 2.0 - 6.0,
            header_y - 1.0,
            tab_w,
            32.0,
        ];
        let fm_rect = [content_x + content_w - tab_w, header_y - 1.0, tab_w, 32.0];
        button(
            ui,
            &mut self.targets,
            lib_rect,
            "Library",
            Action::SetInstalledSubTab(InstalledSubTab::Library),
            self.catalog.installed_sub_tab == InstalledSubTab::Library,
        );
        button(
            ui,
            &mut self.targets,
            fm_rect,
            "File Manager",
            Action::SetInstalledSubTab(InstalledSubTab::FileManager),
            self.catalog.installed_sub_tab == InstalledSubTab::FileManager,
        );

        if self.catalog.installed_sub_tab == InstalledSubTab::Library {
            // Row 1: Search filter input + Sort selector + Quick Import CTA
            let row1_y = 96.0 + slide_y;
            let sort_w = if compact { 96.0 } else { 114.0 };
            let imp_w = if compact { 78.0 } else { 94.0 };
            let search_w = (content_w - sort_w - imp_w - 14.0).max(100.0);
            let search_r = ui.text_field(
                content_x,
                row1_y,
                search_w,
                34.0,
                "",
                &self.catalog.installed_query,
                "Search installed mods, packs & shaders...",
                self.catalog.editing == Some("installed_search"),
            );
            self.targets
                .push((search_r, Action::Field("installed_search")));

            let sort_r = [content_x + search_w + 7.0, row1_y, sort_w, 34.0];
            button(
                ui,
                &mut self.targets,
                sort_r,
                self.catalog.installed_sort.label(),
                Action::CycleInstalledSort,
                false,
            );

            let import_kind = match self.catalog.installed_filter {
                InstalledFilter::Mods | InstalledFilter::All => Kind::Mods,
                InstalledFilter::Packs => Kind::Packs,
                InstalledFilter::Shaders => Kind::Shaders,
            };
            let imp_r = [content_x + search_w + sort_w + 14.0, row1_y, imp_w, 34.0];
            button(
                ui,
                &mut self.targets,
                imp_r,
                "+ Import",
                Action::Import(import_kind),
                true,
            );

            // Row 2: Category filter pills (All, Mods, Resource Packs, Shaders)
            let row2_y = 136.0 + slide_y;
            let pill_gap = 6.0;
            let pill_w = ((content_w - pill_gap * 3.0) / 4.0).max(64.0);
            for (i, filter) in InstalledFilter::ALL.into_iter().enumerate() {
                let count = self
                    .catalog
                    .local
                    .iter()
                    .filter(|item| filter.matches(item.kind))
                    .count();
                let lbl = if compact && filter == InstalledFilter::Packs {
                    format!("Packs ({count})")
                } else {
                    format!("{} ({count})", filter.label())
                };
                let pr = [
                    content_x + i as f32 * (pill_w + pill_gap),
                    row2_y,
                    pill_w,
                    28.0,
                ];
                button(
                    ui,
                    &mut self.targets,
                    pr,
                    &lbl,
                    Action::SetInstalledFilter(filter),
                    self.catalog.installed_filter == filter,
                );
            }

            // Filtered items list
            let q = self.catalog.installed_query.trim().to_lowercase();
            let filtered: Vec<(usize, crate::catalog::LocalItem)> = self
                .catalog
                .local
                .iter()
                .cloned()
                .enumerate()
                .filter(|(_, item)| {
                    self.catalog.installed_filter.matches(item.kind)
                        && (q.is_empty()
                            || item.title.to_lowercase().contains(&q)
                            || item.file.to_lowercase().contains(&q)
                            || item.subtitle.to_lowercase().contains(&q))
                })
                .collect();

            let top = 172.0 + slide_y;
            let bottom = h - 42.0;
            if filtered.is_empty() {
                self.list_view = None;
                let card_h = (bottom - top - 8.0).clamp(110.0, 176.0);
                ui.rounded(
                    content_x,
                    top,
                    content_w,
                    card_h,
                    GLASS_RADIUS,
                    blaze_surface(),
                );
                ui.border(
                    content_x,
                    top,
                    content_w,
                    card_h,
                    GLASS_RADIUS,
                    blaze_border(),
                );
                ui.rounded(
                    content_x + 20.0,
                    top + 18.0,
                    42.0,
                    42.0,
                    12.0,
                    blaze_accent_soft(),
                );
                ui.icon(
                    Icon::Installed,
                    content_x + 30.0,
                    top + 28.0,
                    22.0,
                    blaze_accent(),
                );
                ui.text(
                    Face::SemiBold,
                    16.0,
                    content_x + 74.0,
                    top + 18.0,
                    blaze_text(),
                    if self.catalog.local.is_empty() {
                        "No local add-ons installed yet"
                    } else {
                        "No installed add-ons match your filter"
                    },
                );
                ui.hint_line(
                    content_x + 74.0,
                    top + 42.0,
                    (content_w - 92.0).max(80.0),
                    "Import .hmod/.jar mods, .zip resource packs, or shader packs — or browse files in File Manager.",
                    blaze_muted(),
                );
                let cta_y = top + card_h - 46.0;
                let cta_w = ((content_w - 56.0) / 3.0).clamp(88.0, 168.0);
                button(
                    ui,
                    &mut self.targets,
                    [content_x + 20.0, cta_y, cta_w, 34.0],
                    "Import Mod",
                    Action::Import(Kind::Mods),
                    true,
                );
                button(
                    ui,
                    &mut self.targets,
                    [content_x + 28.0 + cta_w, cta_y, cta_w, 34.0],
                    "Import Pack",
                    Action::Import(Kind::Packs),
                    false,
                );
                button(
                    ui,
                    &mut self.targets,
                    [content_x + 36.0 + cta_w * 2.0, cta_y, cta_w, 34.0],
                    "File Manager",
                    Action::SetInstalledSubTab(InstalledSubTab::FileManager),
                    false,
                );
                return;
            }

            let row_h = 68.0;
            let total_h = filtered.len() as f32 * row_h;
            let max_scroll = (total_h - (bottom - top)).max(0.0);
            self.catalog.scroll = self.catalog.scroll.clamp(0.0, max_scroll);
            self.list_view = Some([top, bottom, max_scroll]);

            ui.set_clip(Some([
                content_x - 2.0,
                top,
                content_w + 4.0,
                (bottom - top).max(1.0),
            ]));
            for (row_idx, (orig_idx, item)) in filtered.into_iter().enumerate() {
                let y = top + row_idx as f32 * row_h - self.catalog.scroll;
                let card_h = 62.0;
                let toggle_r = [content_x + content_w - 196.0, y + 12.0, 56.0, 38.0];
                let export_r = [content_x + content_w - 132.0, y + 14.0, 58.0, 34.0];
                let delete_r = [content_x + content_w - 68.0, y + 14.0, 58.0, 34.0];
                self.list_targets
                    .push((toggle_r, Action::ToggleLocal(orig_idx)));
                self.list_targets
                    .push((export_r, Action::ExportLocal(orig_idx)));
                self.list_targets
                    .push((delete_r, Action::DeleteLocal(orig_idx)));

                if y + card_h < top || y > bottom {
                    continue;
                }
                ui.rounded(content_x, y, content_w, card_h, 14.0, blaze_card_bg());
                ui.border(
                    content_x,
                    y,
                    content_w,
                    card_h,
                    14.0,
                    if item.enabled {
                        blaze_accent()
                    } else {
                        blaze_card_border()
                    },
                );

                // Left file-type icon + badge pill (MOD / PACK / SHADER)
                let kind_icon = match item.kind {
                    Kind::Mods => Icon::Mods,
                    Kind::Packs => Icon::Packs,
                    Kind::Shaders => Icon::Shaders,
                };
                ui.rounded(
                    content_x + 10.0,
                    y + 9.0,
                    44.0,
                    44.0,
                    12.0,
                    if item.enabled {
                        blaze_accent_soft()
                    } else {
                        blaze_surface_high()
                    },
                );
                ui.icon(
                    kind_icon,
                    content_x + 22.0,
                    y + 15.0,
                    20.0,
                    if item.enabled {
                        blaze_accent()
                    } else {
                        blaze_muted()
                    },
                );
                ui.centered_fit(
                    Face::SemiBold,
                    8.0,
                    [content_x + 11.0, y + 36.0, 42.0, 14.0],
                    if item.enabled {
                        blaze_accent()
                    } else {
                        blaze_muted()
                    },
                    item.badge,
                );

                // Status badge pill ("ACTIVE" vs "OFF")
                let status_str = if item.enabled { "ACTIVE" } else { "OFF" };
                let status_w = if item.enabled { 48.0 } else { 34.0 };
                let text_x = content_x + 64.0;
                let max_title_w = (content_w - 280.0 - status_w).max(60.0);
                let fitted_title = ui.fit_text(Face::SemiBold, 14.5, &item.title, max_title_w);
                let title_w = ui.text(
                    Face::SemiBold,
                    14.5,
                    text_x,
                    y + 9.0,
                    blaze_text(),
                    &fitted_title,
                );
                let badge_x = text_x + title_w + 8.0;
                ui.rounded(
                    badge_x,
                    y + 11.0,
                    status_w,
                    18.0,
                    9.0,
                    if item.enabled {
                        blaze_accent_soft()
                    } else {
                        blaze_surface_high()
                    },
                );
                ui.centered_fit(
                    Face::SemiBold,
                    8.5,
                    [badge_x, y + 11.0, status_w, 18.0],
                    if item.enabled {
                        blaze_accent()
                    } else {
                        blaze_muted()
                    },
                    status_str,
                );

                ui.text_clipped(
                    Face::Regular,
                    11.5,
                    text_x,
                    y + 34.0,
                    (content_w - 270.0).max(70.0),
                    blaze_muted(),
                    &item.subtitle,
                );

                // Enable/Disable switch + Export & Delete buttons
                ui.switch(toggle_r[0] + 2.0, y + 17.0, item.enabled);
                if let Some(c) = ui.clip_rect(toggle_r) {
                    self.targets.push((c, Action::ToggleLocal(orig_idx)));
                }
                button(
                    ui,
                    &mut self.targets,
                    export_r,
                    "Export",
                    Action::ExportLocal(orig_idx),
                    false,
                );
                button(
                    ui,
                    &mut self.targets,
                    delete_r,
                    "Delete",
                    Action::DeleteLocal(orig_idx),
                    false,
                );
            }
            ui.set_clip(None);
            return;
        }

        // ---- Sub-View 2: Integrated Mini File Manager (`InstalledSubTab::FileManager`) ----
        // Row 1: Back, Forward, Up + Clickable Breadcrumb Trail + Import & New Folder CTAs
        let nav_y = 94.0 + slide_y;
        let nav_btn_w = 32.0;
        let has_back = !self.catalog.fm_back_stack.is_empty();
        let has_fwd = !self.catalog.fm_forward_stack.is_empty();
        let has_up = !self.catalog.fm_current_dir.is_empty();
        button(
            ui,
            &mut self.targets,
            [content_x, nav_y, nav_btn_w, 30.0],
            "←",
            Action::FmBack,
            has_back,
        );
        button(
            ui,
            &mut self.targets,
            [content_x + 36.0, nav_y, nav_btn_w, 30.0],
            "→",
            Action::FmForward,
            has_fwd,
        );
        button(
            ui,
            &mut self.targets,
            [content_x + 72.0, nav_y, nav_btn_w, 30.0],
            "↑",
            Action::FmUp,
            has_up,
        );

        let right_cta_w = if compact { 148.0 } else { 172.0 };
        let imp_btn_w = (right_cta_w - 6.0) * 0.48;
        let new_folder_w = right_cta_w - 6.0 - imp_btn_w;
        button(
            ui,
            &mut self.targets,
            [content_x + content_w - right_cta_w, nav_y, imp_btn_w, 30.0],
            "Import",
            Action::FmImportHere,
            true,
        );
        button(
            ui,
            &mut self.targets,
            [
                content_x + content_w - new_folder_w,
                nav_y,
                new_folder_w,
                30.0,
            ],
            "+ Folder",
            Action::FmOpenNewFolder,
            false,
        );

        // Clickable Breadcrumb Bar between Up button and Right CTAs
        let bc_x = content_x + 110.0;
        let bc_w = (content_x + content_w - right_cta_w - 8.0 - bc_x).max(60.0);
        ui.rounded(bc_x, nav_y, bc_w, 30.0, 10.0, blaze_surface());
        ui.border(bc_x, nav_y, bc_w, 30.0, 10.0, blaze_border());
        let mut cur_bx = bc_x + 6.0;
        let root_lbl = format!("P{}", self.catalog.config.profile + 1);
        let root_bw = (ui.measure(Face::SemiBold, 11.5, &root_lbl) + 12.0).min(bc_w - 10.0);
        let root_br = [cur_bx, nav_y + 4.0, root_bw, 22.0];
        ui.rounded(
            root_br[0],
            root_br[1],
            root_br[2],
            root_br[3],
            6.0,
            if self.catalog.fm_current_dir.is_empty() {
                blaze_accent_soft()
            } else {
                blaze_surface_high()
            },
        );
        ui.centered_fit(Face::SemiBold, 11.0, root_br, blaze_accent(), &root_lbl);
        self.targets.push((root_br, Action::FmBreadcrumb(0)));
        cur_bx += root_bw + 4.0;

        if !self.catalog.fm_current_dir.is_empty() {
            let parts: Vec<&str> = self
                .catalog
                .fm_current_dir
                .split('/')
                .filter(|s| !s.is_empty())
                .collect();
            for (idx, seg) in parts.iter().enumerate() {
                if cur_bx + 28.0 > bc_x + bc_w {
                    break;
                }
                ui.text(Face::Regular, 11.0, cur_bx, nav_y + 7.0, blaze_muted(), "/");
                cur_bx += 8.0;
                let avail = (bc_x + bc_w - 6.0 - cur_bx).max(24.0);
                let fit_seg = ui.fit_text(Face::Medium, 11.0, seg, (avail - 10.0).max(16.0));
                let seg_w = (ui.measure(Face::Medium, 11.0, &fit_seg) + 10.0).min(avail);
                let seg_r = [cur_bx, nav_y + 4.0, seg_w, 22.0];
                let is_last = idx + 1 == parts.len();
                ui.rounded(
                    seg_r[0],
                    seg_r[1],
                    seg_r[2],
                    seg_r[3],
                    6.0,
                    if is_last {
                        blaze_accent_soft()
                    } else {
                        blaze_surface_high()
                    },
                );
                ui.centered_fit(
                    Face::Medium,
                    11.0,
                    seg_r,
                    if is_last { blaze_text() } else { blaze_muted() },
                    &fit_seg,
                );
                self.targets.push((seg_r, Action::FmBreadcrumb(idx + 1)));
                cur_bx += seg_w + 4.0;
            }
        }

        // Row 2: Quick Root Folder Pills (Root, Worlds, Mods, Packs, Shaders, Controls, Shots, Reports)
        let root_y = 128.0 + slide_y;
        let root_dirs = crate::document_store::FM_ROOT_DIRS;
        let r_gap = 4.0;
        let r_w = ((content_w - r_gap * (root_dirs.len() as f32 - 1.0)) / root_dirs.len() as f32)
            .max(38.0);
        for (i, (rel, label)) in root_dirs.iter().enumerate() {
            let rx = content_x + i as f32 * (r_w + r_gap);
            let active = self.catalog.fm_current_dir == *rel
                || (!rel.is_empty()
                    && self
                        .catalog
                        .fm_current_dir
                        .strip_prefix(*rel)
                        .is_some_and(|rest| rest.starts_with('/')));
            button(
                ui,
                &mut self.targets,
                [rx, root_y, r_w, 26.0],
                label,
                Action::FmNavigateRoot(rel),
                active,
            );
        }

        // Row 3: Multi-Select & Clipboard Action Toolbar
        let tool_y = 158.0 + slide_y;
        let sel_count = self.catalog.fm_selected.len();
        let clip_count = self
            .catalog
            .fm_clipboard
            .as_ref()
            .map_or(0, |(v, _)| v.len());
        let paste_label = if clip_count > 0 {
            format!("Paste ({clip_count})")
        } else {
            "Paste".into()
        };
        let del_label = if sel_count > 0 {
            format!("Delete ({sel_count})")
        } else {
            "Delete".into()
        };
        let tools: [(&str, Action, bool); 8] = [
            ("All", Action::FmSelectAll, false),
            ("Clear", Action::FmClearSelection, false),
            ("Copy", Action::FmCopy, sel_count > 0),
            ("Cut", Action::FmCut, sel_count > 0),
            (&paste_label, Action::FmPaste, clip_count > 0),
            ("Rename", Action::FmOpenRenameSelected, sel_count == 1),
            ("Export", Action::FmExportSelected, sel_count > 0),
            (&del_label, Action::FmRequestDeleteSelected, sel_count > 0),
        ];
        let t_gap = 4.0;
        let t_w = ((content_w - t_gap * (tools.len() as f32 - 1.0)) / tools.len() as f32).max(38.0);
        for (i, (lbl, act, hi)) in tools.into_iter().enumerate() {
            let tx = content_x + i as f32 * (t_w + t_gap);
            button(ui, &mut self.targets, [tx, tool_y, t_w, 26.0], lbl, act, hi);
        }

        // Scrollable File & Directory Rows
        let top = 190.0 + slide_y;
        let bottom = h - 42.0;
        if self.catalog.fm_entries.is_empty() {
            self.list_view = None;
            let empty_h = (bottom - top - 8.0).clamp(96.0, 152.0);
            ui.rounded(
                content_x,
                top,
                content_w,
                empty_h,
                GLASS_RADIUS,
                blaze_surface(),
            );
            ui.border(
                content_x,
                top,
                content_w,
                empty_h,
                GLASS_RADIUS,
                blaze_border(),
            );
            ui.icon(
                Icon::Folder,
                content_x + 20.0,
                top + 20.0,
                24.0,
                blaze_accent(),
            );
            ui.text(
                Face::SemiBold,
                15.0,
                content_x + 54.0,
                top + 18.0,
                blaze_text(),
                "This folder is empty",
            );
            ui.hint_line(
                content_x + 54.0,
                top + 40.0,
                (content_w - 72.0).max(80.0),
                "Use Import to copy files from Android storage, + Folder to create a directory, or Paste from clipboard.",
                blaze_muted(),
            );
            return;
        }

        let row_h = 52.0;
        let total_h = self.catalog.fm_entries.len() as f32 * row_h;
        let max_scroll = (total_h - (bottom - top)).max(0.0);
        self.catalog.scroll = self.catalog.scroll.clamp(0.0, max_scroll);
        self.list_view = Some([top, bottom, max_scroll]);

        ui.set_clip(Some([
            content_x - 2.0,
            top,
            content_w + 4.0,
            (bottom - top).max(1.0),
        ]));
        let entries_snapshot = self.catalog.fm_entries.clone();
        for (idx, entry) in entries_snapshot.iter().enumerate() {
            let y = top + idx as f32 * row_h - self.catalog.scroll;
            let card_h = 46.0;
            let selected = self.catalog.fm_selected.contains(&entry.rel_path);
            let check_r = [content_x + 6.0, y + 7.0, 32.0, 32.0];
            let actions_w = if compact { 164.0 } else { 204.0 };
            let open_r = [
                content_x + 40.0,
                y,
                (content_w - 44.0 - actions_w).max(60.0),
                card_h,
            ];
            let btn_w = (actions_w - 14.0) / 3.0;
            let info_r = [content_x + content_w - actions_w, y + 8.0, btn_w, 30.0];
            let ren_r = [info_r[0] + btn_w + 4.0, y + 8.0, btn_w, 30.0];
            let del_r = [ren_r[0] + btn_w + 4.0, y + 8.0, btn_w, 30.0];

            self.list_targets.push((open_r, Action::FmOpenEntry(idx)));
            self.list_targets
                .push((check_r, Action::FmToggleSelect(idx)));
            self.list_targets.push((info_r, Action::FmOpenDetails(idx)));
            self.list_targets.push((ren_r, Action::FmOpenRename(idx)));
            self.list_targets
                .push((del_r, Action::FmRequestDeleteSingle(idx)));

            if y + card_h < top || y > bottom {
                continue;
            }
            ui.rounded(
                content_x,
                y,
                content_w,
                card_h,
                12.0,
                if selected {
                    blaze_nav_active()
                } else {
                    blaze_card_bg()
                },
            );
            ui.border(
                content_x,
                y,
                content_w,
                card_h,
                12.0,
                if selected {
                    blaze_accent()
                } else {
                    blaze_card_border()
                },
            );

            ui.checkbox(content_x + 11.0, y + 13.0, 20.0, selected, "");
            if let Some(c) = ui.clip_rect(check_r) {
                self.targets.push((c, Action::FmToggleSelect(idx)));
            }

            let entry_icon = if entry.is_dir {
                Icon::Folder
            } else if entry.name.ends_with(".hmod") || entry.name.ends_with(".jar") {
                Icon::Mods
            } else if entry.name.ends_with(".hshader") {
                Icon::Shaders
            } else if entry.name.ends_with(".zip") {
                Icon::Packs
            } else {
                Icon::FileText
            };
            ui.rounded(
                content_x + 38.0,
                y + 7.0,
                32.0,
                32.0,
                9.0,
                if entry.is_dir {
                    blaze_accent_soft()
                } else {
                    blaze_surface_high()
                },
            );
            ui.icon(
                entry_icon,
                content_x + 46.0,
                y + 15.0,
                16.0,
                if entry.is_dir {
                    blaze_accent()
                } else {
                    blaze_text()
                },
            );

            let tx = content_x + 78.0;
            let max_tw = (content_w - 86.0 - actions_w).max(50.0);
            ui.text_clipped(
                Face::SemiBold,
                13.5,
                tx,
                y + 5.0,
                max_tw,
                blaze_text(),
                &entry.name,
            );
            let meta_line = if entry.is_dir {
                format!("Folder  •  {} item(s)  •  Tap to open", entry.size_bytes)
            } else {
                format!(
                    "{}  •  {}  •  {}",
                    entry.kind_label,
                    crate::catalog::format_file_size(entry.size_bytes),
                    crate::document_store::format_timestamp_utc(entry.modified_secs)
                )
            };
            ui.text_clipped(
                Face::Regular,
                10.5,
                tx,
                y + 24.0,
                max_tw,
                blaze_muted(),
                &meta_line,
            );
            if let Some(c) = ui.clip_rect(open_r) {
                self.targets.push((c, Action::FmOpenEntry(idx)));
            }

            button(
                ui,
                &mut self.targets,
                info_r,
                "Details",
                Action::FmOpenDetails(idx),
                false,
            );
            button(
                ui,
                &mut self.targets,
                ren_r,
                "Rename",
                Action::FmOpenRename(idx),
                false,
            );
            button(
                ui,
                &mut self.targets,
                del_r,
                "Delete",
                Action::FmRequestDeleteSingle(idx),
                false,
            );
        }
        ui.set_clip(None);
    }
}

pub fn draw_manager_view(
    launcher: &mut Launcher,
    ui: &mut Ui,
    content_x: f32,
    content_w: f32,
    h: f32,
    compact: bool,
    slide_y: f32,
) {
    launcher.draw_installed(ui, content_x, content_w, h, compact, slide_y);
}
