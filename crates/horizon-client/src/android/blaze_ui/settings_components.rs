use super::*;
use horizon_client::ui_theme::{Face, Icon, Ui, color};

/// Section Header: small accent mono category badge + title
pub fn draw_section_header(ui: &mut Ui, x: f32, y: f32, w: f32, badge: &str, title: &str) {
    ui.text(Face::SemiBold, 9.5, x, y + 2.0, blaze_accent(), badge);
    ui.text(Face::SemiBold, 14.0, x, y + 16.0, blaze_text(), title);
    ui.fill(x, y + 36.0, w, 1.0, color::hex(0xFFFFFF, 16));
}

/// Base Setting Row: label (14px) on left, optional helper (11px) below, min height 56px, 44px touch target.
pub fn draw_setting_row_base(
    ui: &mut Ui,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    helper: Option<&str>,
) -> [f32; 4] {
    let t_y = if helper.is_some() {
        y + 10.0
    } else {
        y + (h - 18.0) * 0.5
    };
    ui.text_clipped(
        Face::SemiBold,
        13.5,
        x + 12.0,
        t_y,
        w * 0.45,
        blaze_text(),
        label,
    );
    if let Some(sub) = helper {
        ui.text_clipped(
            Face::Regular,
            10.5,
            x + 12.0,
            t_y + 18.0,
            w * 0.45,
            blaze_muted(),
            sub,
        );
    }
    // Subtle bottom border between rows
    ui.fill(
        x + 12.0,
        y + h - 1.0,
        w - 24.0,
        1.0,
        color::hex(0xFFFFFF, 10),
    );
    [x, y, w, h]
}

/// SwatchRow: 5 round color swatches (Cyan, Green, Red, Violet, Gold), selected has outer ring
pub fn draw_swatch_row(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    list_targets: &mut Vec<([f32; 4], Action)>,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    selected_idx: u8,
) {
    draw_setting_row_base(ui, x, y, w, h, label, None);
    let swatches = [
        (0u8, 0x38bdf8, "Cyan"),
        (1u8, 0x34d399, "Green"),
        (2u8, 0xfb7185, "Red"),
        (3u8, 0xa78bfa, "Violet"),
        (4u8, 0xfbbf24, "Gold"),
    ];
    let swatch_sz = 24.0;
    let gap = 8.0;
    let total_w = 5.0 * swatch_sz + 4.0 * gap;
    let start_x = x + w - total_w - 12.0;
    let start_y = y + (h - swatch_sz) * 0.5;

    for (i, (idx, rgb, _name)) in swatches.into_iter().enumerate() {
        let sx = start_x + i as f32 * (swatch_sz + gap);
        let rect = [sx, start_y, swatch_sz, swatch_sz];
        let is_sel = selected_idx == idx;
        if is_sel {
            ui.border(
                sx - 2.0,
                start_y - 2.0,
                swatch_sz + 4.0,
                swatch_sz + 4.0,
                (swatch_sz + 4.0) * 0.5,
                color::hex(rgb, 255),
            );
        }
        ui.rounded(
            sx,
            start_y,
            swatch_sz,
            swatch_sz,
            swatch_sz * 0.5,
            color::hex(rgb, 255),
        );
        if let Some(c) = ui.clip_rect(rect) {
            targets.push((c, Action::SelectAccentPalette(idx)));
            list_targets.push((c, Action::SelectAccentPalette(idx)));
        }
    }
}

/// Segmented: 2-4 pill buttons, one selected (accent fill)
pub fn draw_segmented<const N: usize>(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    list_targets: &mut Vec<([f32; 4], Action)>,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    helper: Option<&str>,
    items: [(&str, Action, bool); N],
) {
    draw_setting_row_base(ui, x, y, w, h, label, helper);
    let btn_h = 32.0;
    let btn_w = 64.0;
    let gap = 4.0;
    let total_w = N as f32 * btn_w + (N - 1) as f32 * gap;
    let start_x = x + w - total_w - 12.0;
    let start_y = y + (h - btn_h) * 0.5;

    for (i, (txt, action, is_sel)) in items.into_iter().enumerate() {
        let bx = start_x + i as f32 * (btn_w + gap);
        let rect = [bx, start_y, btn_w, btn_h];
        if is_sel {
            ui.rounded(bx, start_y, btn_w, btn_h, 8.0, blaze_accent());
            ui.centered_fit(Face::SemiBold, 11.0, rect, blaze_on_accent(), txt);
        } else {
            ui.rounded(bx, start_y, btn_w, btn_h, 8.0, color::hex(0xFFFFFF, 14));
            ui.border(bx, start_y, btn_w, btn_h, 8.0, color::hex(0xFFFFFF, 26));
            ui.centered_fit(Face::Medium, 11.0, rect, blaze_muted(), txt);
        }
        if let Some(c) = ui.clip_rect(rect) {
            targets.push((c, action));
            list_targets.push((c, action));
        }
    }
}

/// Switch Row: 52x32 pill switch
pub fn draw_switch_row(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    list_targets: &mut Vec<([f32; 4], Action)>,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    helper: Option<&str>,
    on: bool,
    action: Action,
) {
    draw_setting_row_base(ui, x, y, w, h, label, helper);
    let sw_w = 52.0;
    let sw_h = 32.0;
    let sw_x = x + w - sw_w - 14.0;
    let sw_y = y + (h - sw_h) * 0.5;
    ui.switch(sw_x, sw_y, on);
    let rect = [sw_x - 4.0, sw_y - 4.0, sw_w + 8.0, sw_h + 8.0];
    if let Some(c) = ui.clip_rect(rect) {
        targets.push((c, action));
        list_targets.push((c, action));
    }
}

/// Slider Row: track + thumb + value label, touch drag
pub fn draw_slider_row(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    list_targets: &mut Vec<([f32; 4], Action)>,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    helper: Option<&str>,
    val_label: &str,
    fraction: f32,
    action: Action,
) {
    draw_setting_row_base(ui, x, y, w, h, label, helper);
    let track_w = (w * 0.32).clamp(100.0, 180.0);
    let track_h = 6.0;
    let track_x = x + w - track_w - 56.0;
    let track_y = y + (h - track_h) * 0.5;

    // Value label
    ui.centered_fit(
        Face::SemiBold,
        11.0,
        [track_x + track_w + 4.0, y, 48.0, h],
        blaze_accent(),
        val_label,
    );

    // Track
    ui.rounded(
        track_x,
        track_y,
        track_w,
        track_h,
        3.0,
        color::hex(0xFFFFFF, 26),
    );
    let fill_w = (track_w * fraction.clamp(0.0, 1.0)).max(6.0);
    ui.rounded(track_x, track_y, fill_w, track_h, 3.0, blaze_accent());

    // Thumb
    let thumb_sz = 16.0;
    let thumb_x = (track_x + fill_w - thumb_sz * 0.5).clamp(track_x, track_x + track_w - thumb_sz);
    let thumb_y = track_y + (track_h - thumb_sz) * 0.5;
    ui.rounded(
        thumb_x,
        thumb_y,
        thumb_sz,
        thumb_sz,
        thumb_sz * 0.5,
        color::hex(0xFFFFFF, 255),
    );

    let hit_rect = [track_x - 8.0, y + (h - 44.0) * 0.5, track_w + 16.0, 44.0];
    SLIDER_TRACK_TARGETS.with(|list| {
        list.borrow_mut().push((hit_rect, action, track_x, track_w));
    });
    if let Some(c) = ui.clip_rect(hit_rect) {
        targets.push((c, action));
        list_targets.push((c, action));
    }
}

/// Dropdown Row: value + chevron icon, opens a list/chooser
pub fn draw_dropdown_row(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    list_targets: &mut Vec<([f32; 4], Action)>,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    helper: Option<&str>,
    val: &str,
    action: Action,
) {
    draw_setting_row_base(ui, x, y, w, h, label, helper);
    let btn_w = (ui.measure(Face::SemiBold, 12.0, val) + 36.0).clamp(100.0, 180.0);
    let btn_h = 36.0;
    let btn_x = x + w - btn_w - 12.0;
    let btn_y = y + (h - btn_h) * 0.5;
    let rect = [btn_x, btn_y, btn_w, btn_h];

    ui.rounded(btn_x, btn_y, btn_w, btn_h, 10.0, color::hex(0xFFFFFF, 14));
    ui.border(btn_x, btn_y, btn_w, btn_h, 10.0, color::hex(0xFFFFFF, 26));
    ui.text_clipped(
        Face::SemiBold,
        12.0,
        btn_x + 12.0,
        btn_y + 9.0,
        btn_w - 32.0,
        blaze_text(),
        val,
    );
    ui.icon(
        Icon::ChevronDown,
        btn_x + btn_w - 20.0,
        btn_y + 11.0,
        14.0,
        blaze_muted(),
    );

    if let Some(c) = ui.clip_rect(rect) {
        targets.push((c, action));
        list_targets.push((c, action));
    }
}

/// Action Button Row: for Choose File, Reset, Export, Import
pub fn draw_action_button_row(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    list_targets: &mut Vec<([f32; 4], Action)>,
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    label: &str,
    helper: Option<&str>,
    btn_label: &str,
    action: Action,
    accent: bool,
) {
    draw_setting_row_base(ui, x, y, w, h, label, helper);
    let btn_w = (ui.measure(Face::SemiBold, 12.0, btn_label) + 28.0).clamp(90.0, 160.0);
    let btn_h = 36.0;
    let btn_x = x + w - btn_w - 12.0;
    let btn_y = y + (h - btn_h) * 0.5;
    let rect = [btn_x, btn_y, btn_w, btn_h];

    button(ui, targets, rect, btn_label, action, accent);
    if let Some(c) = ui.clip_rect(rect) {
        list_targets.push((c, action));
    }
}
