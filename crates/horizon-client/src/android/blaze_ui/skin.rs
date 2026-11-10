use super::*;
use horizon_client::ui_theme::Ui;

pub fn draw_skin_view(
    launcher: &mut Launcher,
    ui: &mut Ui,
    content_x: f32,
    content_w: f32,
    slide_y: f32,
) {
    launcher.wardrobe.draw(
        ui,
        &mut launcher.targets,
        launcher.catalog.scroll,
        content_x,
        content_w,
        62.0 + slide_y,
    );
    launcher.list_view = Some(launcher.wardrobe.view);
    launcher.catalog.scroll = launcher.catalog.scroll.min(launcher.wardrobe.view[2]);
}
