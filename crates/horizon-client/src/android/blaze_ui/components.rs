use super::*;
use horizon_client::ui_theme::{Face, Icon, LayoutRect, Ui, color, lerp_color};

pub const GLASS_RADIUS: f32 = 14.0;
pub const TILE_RADIUS: f32 = 10.0;

#[inline]
pub fn glass_fill() -> u32 {
    let t = horizon_client::ui_theme::active_theme();
    let rgb = if t.is_light { 0xFFFFFF } else { 0x0E131C };
    color::with_alpha(color::hex(rgb, 255), t.glass_opacity)
}

#[inline]
pub fn glass_edge(hot: bool) -> u32 {
    let t = horizon_client::ui_theme::active_theme();
    match (t.is_light, hot) {
        (false, false) => color::hex(0xFFFFFF, 23),
        (false, true) => color::hex(0xFFFFFF, 64),
        (true, false) => color::hex(0x0F172A, 31),
        (true, true) => color::hex(0x0F172A, 77),
    }
}

#[inline]
pub fn blaze_bg() -> u32 {
    horizon_client::ui_theme::active_theme().bg_solid
}
#[inline]
pub fn blaze_surface() -> u32 {
    glass_fill()
}
#[inline]
pub fn blaze_card_bg() -> u32 {
    horizon_client::ui_theme::active_theme().surface_dim
}
#[inline]
pub fn blaze_card_border() -> u32 {
    horizon_client::ui_theme::active_theme().border
}
#[inline]
pub fn blaze_surface_high() -> u32 {
    horizon_client::ui_theme::active_theme().surface_bright
}
#[inline]
pub fn blaze_border() -> u32 {
    glass_edge(false)
}
#[inline]
pub fn blaze_nav_active() -> u32 {
    horizon_client::ui_theme::active_theme().surface_selected
}
#[inline]
pub fn blaze_accent() -> u32 {
    horizon_client::ui_theme::active_theme().accent
}
#[inline]
pub fn blaze_accent_soft() -> u32 {
    horizon_client::ui_theme::active_theme().accent_soft
}
#[inline]
pub fn blaze_on_accent() -> u32 {
    horizon_client::ui_theme::active_theme().on_accent
}
#[inline]
pub fn blaze_track() -> u32 {
    horizon_client::ui_theme::active_theme().surface_dim
}
#[inline]
pub fn blaze_pause_bg() -> u32 {
    horizon_client::ui_theme::active_theme().surface_bright
}
#[inline]
pub fn blaze_cancel_bg() -> u32 {
    color::with_alpha(horizon_client::ui_theme::active_theme().danger, 0.28)
}
#[inline]
pub fn blaze_text() -> u32 {
    horizon_client::ui_theme::active_theme().text_primary
}
#[inline]
pub fn blaze_muted() -> u32 {
    horizon_client::ui_theme::active_theme().text_muted
}

#[inline]
pub fn tile_fill() -> u32 {
    if horizon_client::ui_theme::active_theme().is_light {
        color::hex(0x0F172A, 13)
    } else {
        color::hex(0xFFFFFF, 13)
    }
}

pub fn glass_card(ui: &mut Ui, r: LayoutRect, radius: f32) -> bool {
    ui.rounded(r.x, r.y, r.w, r.h, radius, glass_fill());
    ui.border(r.x, r.y, r.w, r.h, radius, glass_edge(false));
    false
}

pub fn corner_inset(d: f32, len: f32, r: f32) -> f32 {
    let edge = d.min(len - d);
    if r <= 0.0 || edge >= r {
        0.0
    } else {
        r - (r * r - (r - edge) * (r - edge)).max(0.0).sqrt()
    }
}

#[allow(clippy::too_many_arguments)]
pub fn rounded_hgrad(
    ui: &mut Ui,
    r: LayoutRect,
    radius: f32,
    from_frac: f32,
    c0: u32,
    cm: u32,
    c1: u32,
) {
    let x_start = r.x + r.w * from_frac.clamp(0.0, 0.95);
    let span = (r.x + r.w - x_start).max(1.0);
    let step = 2.0f32;
    let mut sx = x_start;
    while sx < r.x + r.w - 0.01 {
        let sw = step.min(r.x + r.w - sx);
        let t = ((sx + sw * 0.5 - x_start) / span).clamp(0.0, 1.0);
        let col = if t < 0.5 {
            lerp_color(c0, cm, t * 2.0)
        } else {
            lerp_color(cm, c1, (t - 0.5) * 2.0)
        };
        let inset = corner_inset(sx + sw * 0.5 - r.x, r.w, radius) + 0.5;
        ui.fill(sx, r.y + inset, sw + 0.4, (r.h - inset * 2.0).max(0.0), col);
        sx += sw;
    }
}

#[allow(clippy::too_many_arguments)]
pub fn rounded_vgrad(
    ui: &mut Ui,
    r: LayoutRect,
    radius: f32,
    from_frac: f32,
    c0: u32,
    cm: u32,
    c1: u32,
) {
    let y_start = r.y + r.h * from_frac.clamp(0.0, 0.95);
    let span = (r.y + r.h - y_start).max(1.0);
    let step = 1.5f32;
    let mut sy = y_start;
    while sy < r.y + r.h - 0.01 {
        let sh = step.min(r.y + r.h - sy);
        let t = ((sy + sh * 0.5 - y_start) / span).clamp(0.0, 1.0);
        let col = if t < 0.5 {
            lerp_color(c0, cm, t * 2.0)
        } else {
            lerp_color(cm, c1, (t - 0.5) * 2.0)
        };
        let d_bottom = r.y + r.h - (sy + sh * 0.5);
        let inset = if d_bottom < radius {
            radius
                - (radius * radius - (radius - d_bottom) * (radius - d_bottom))
                    .max(0.0)
                    .sqrt()
        } else {
            0.0
        } + 0.5;
        ui.fill(r.x + inset, sy, (r.w - inset * 2.0).max(0.0), sh + 0.4, col);
        sy += sh;
    }
}

pub fn page_icon(page: Page) -> Icon {
    match page {
        Page::Play => Icon::Play,
        Page::Mods => Icon::Mods,
        Page::Packs => Icon::Packs,
        Page::Shaders => Icon::Shaders,
        Page::Installed => Icon::Installed,
        Page::Settings => Icon::Settings,
        Page::Skins => Icon::Installed,
    }
}

pub fn icon_action_button(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    rect: [f32; 4],
    bg: u32,
    icon: Icon,
    action: Action,
) {
    let [x, y, w, h] = rect;
    ui.rounded(x, y, w, h, GLASS_RADIUS, bg);
    let icon_size = 18.0;
    ui.icon(
        icon,
        x + (w - icon_size) * 0.5,
        y + (h - icon_size) * 0.5,
        icon_size,
        blaze_text(),
    );
    targets.push((rect, action));
}

pub fn button(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    rect: [f32; 4],
    label: &str,
    action: Action,
    accent: bool,
) {
    let [x, y, w, h] = rect;
    let radius = (h * 0.30).clamp(9.0, GLASS_RADIUS);
    if accent {
        let white = color::hex(0xFFFFFF, 255);
        let a = blaze_accent();
        let g1 = lerp_color(a, white, 0.58);
        ui.rounded(x, y, w, h, radius, g1);
        ui.fill_gradient(
            x,
            y,
            w,
            h,
            horizon_client::ui_theme::GradientDirection::Horizontal,
            lerp_color(a, white, 0.86),
            a,
            Some((0.5, g1)),
            16,
        );
        ui.border(x, y, w, h, radius, color::hex(0xFFFFFF, 40));
    } else {
        ui.rounded(x, y, w, h, radius, glass_fill());
        ui.border(x, y, w, h, radius, glass_edge(false));
    }
    let (clean_label, is_dropdown) = if let Some(s) = label.strip_suffix(" v") {
        (s, true)
    } else if let Some(s) = label.strip_suffix(" \u{25be}") {
        (s, true)
    } else {
        (label, false)
    };

    let padding = if is_dropdown {
        10.0
    } else {
        (w * 0.10).clamp(8.0, 14.0)
    };
    let font = if w < 90.0 {
        13.0
    } else if h <= 36.0 {
        14.0
    } else {
        15.0
    };
    let avail_w = if is_dropdown {
        (w - 2.0 * padding - 16.0).max(12.0)
    } else {
        (w - 2.0 * padding).max(16.0)
    };
    let fitted = ui.fit_text(Face::Medium, font, clean_label, avail_w);
    let text_w = ui.measure(Face::Medium, font, &fitted);
    let left_align = matches!(action, Action::Field(_)) || is_dropdown;
    let tx = if left_align {
        x + padding
    } else {
        x + ((w - text_w) * 0.5).max(padding)
    };
    let ty = y + ((h - font * 1.45) * 0.5).max(2.0);
    let text_col = if accent {
        blaze_on_accent()
    } else {
        blaze_text()
    };
    ui.text(Face::Medium, font, tx, ty, text_col, &fitted);
    if is_dropdown {
        let icon_sz = 12.0;
        let icon_x = x + w - padding - icon_sz;
        let icon_y = y + (h - icon_sz) * 0.5;
        ui.icon(
            Icon::ChevronDown,
            icon_x,
            icon_y,
            icon_sz,
            if accent {
                blaze_on_accent()
            } else {
                blaze_muted()
            },
        );
    }
    if let Some(c) = ui.clip_rect(rect) {
        targets.push((c, action));
    }
}

pub fn toggle(
    ui: &mut Ui,
    targets: &mut Vec<([f32; 4], Action)>,
    rect: [f32; 4],
    on: bool,
    action: Action,
) {
    let [x, y, w, h] = rect;
    ui.switch(x + w - 52.0, y + (h - 32.0) * 0.5, on);
    if let Some(c) = ui.clip_rect([x, y, w, h]) {
        targets.push((c, action));
    }
}
