# Blaze Launcher UI Port Inventory

Comprehensive audit of pages, navigation state, sidebar items, top-right quick actions, settings categories, and individual setting items for the Blaze Landscape UI redesign.

## 1. Navigation & Screen Architecture

| Name | Config Key | Type | Default | Page / Category |
|---|---|---|---|---|
| Home (formerly Play) | `catalog.page` | `Page` | `Page::Play` | Navigation / Home |
| Browse (Mods) | `catalog.page` | `Page` | `Page::Mods` | Navigation / Browse |
| Browse (Resource Packs) | `catalog.page` | `Page` | `Page::Packs` | Navigation / Browse |
| Browse (Shaders) | `catalog.page` | `Page` | `Page::Shaders` | Navigation / Browse |
| Manager (formerly Installed) | `catalog.page` | `Page` | `Page::Installed` | Navigation / Manager |
| Skin (formerly Skins) | `catalog.page` | `Page` | `Page::Skins` | Navigation / Skin |
| Settings | `catalog.page` | `Page` | `Page::Settings` | Navigation / Settings |

## 2. Top Bar & Quick Actions

| Name | Config Key | Type | Default | Page / Category |
|---|---|---|---|---|
| Brand Title ("Blaze Launcher") | N/A (Static UI) | String | "Blaze Launcher" | Top Bar / Brand |
| Engine Subtitle ("26.3 • Rust Engine") | N/A (Static UI) | String | "26.3 • Rust Engine" | Top Bar / Brand |
| Active Profile Selector | `config.profile` | `u32` | `0` ("Profile 1") | Top Bar / Quick Action |
| Edit Touch Controls Button | N/A (Action) | `Action::OpenControlEditor` | N/A | Top Bar / Quick Action |
| Account Avatar & Login Pill | `config.name` | `String` | "Steve" | Top Bar / Quick Action |
| Dismissible Starling Notice | N/A (Banner) | UI State | Visible | Home / Hero Overlay |
| Download Starling Action | N/A (Action) | `Action::StarlingDownload` | N/A | Home / Notice Action |
| Export Worlds (SAF) Action | N/A (Action) | `Action::WorldFiles` | N/A | Home & Manager / Action |
| Primary Play / Download Button | N/A (Action) | `Action::Play` | "Download / Play" | Home / Action Deck |
| Edit Profile Button | N/A (Action) | `Action::Profile` | N/A | Home / Action Deck |
| Browser Resources Shortcut Card | N/A (Action) | `Action::Page(Page::Mods)` | N/A | Home / Shortcut Card |
| Manager Shortcut Card | N/A (Action) | `Action::Page(Page::Installed)`| N/A | Home / Shortcut Card |

## 3. Settings Categories & Items

| Name | Config Key | Type | Default | Page / Category |
|---|---|---|---|---|
| **Appearance** | | | | |
| Accent Palette | `theme.preset` | `u8` (0-4) | `0` (Cyan `#38bdf8`) | Settings / Appearance |
| Background Style | `theme.bg_style` | `String` | `"mc"` (Minecraft Aquatic) | Settings / Appearance |
| Custom Background Image | `theme.custom_bg_uri` | `Option<String>` | `None` | Settings / Appearance |
| Glass Panel Opacity | `theme.glass_opacity` | `f32` (0.40–0.98) | `0.85` | Settings / Appearance |
| Glass Backdrop Blur | `theme.glass_blur` | `f32` (0.0–32.0 px) | `16.0` | Settings / Appearance |
| UI DPI Scaling | `ui_scale_percent` | `u32` (0, 85, 100, 115, 130) | `100` (Auto / Phone DPI) | Settings / Appearance |
| Disable Launcher Animations | `disable_launcher_animations` | `bool` | `false` | Settings / Appearance |
| Disable Game Animations | `disable_game_animations` | `bool` | `false` | Settings / Appearance |
| Theme Studio Sub-Page | N/A (Navigation) | `Action::SettingsCat` | `SettingsCategory::Theme` | Settings / Appearance |
| Theme Appearance Mode | `theme.appearance` | `ThemeAppearance` | `System` | Settings / Theme Studio |
| Theme Background Mode | `theme.background_mode` | `BackgroundMode` | `System` | Settings / Theme Studio |
| Gradient Direction | `theme.gradient_direction` | `GradientDirection` | `LinearBottomRight` | Settings / Theme Studio |
| Three Color Gradient Stops | `theme.use_three_stops` | `bool` | `false` | Settings / Theme Studio |
| Gradient Midpoint Position | `theme.gradient_mid_pos` | `f32` (0.0–1.0) | `0.5` | Settings / Theme Studio |
| Custom Surface Color Hex | `theme.custom_surface_hex` | `String` | `"#1a1d24"` | Settings / Theme Studio |
| Custom Accent Color Hex | `theme.custom_accent_hex` | `String` | `"#38bdf8"` | Settings / Theme Studio |
| Custom Background Color Hex | `theme.custom_bg_hex` | `String` | `"#0c0f17"` | Settings / Theme Studio |
| Reset Theme to Defaults | N/A (Action) | `Action::ResetTheme` | N/A | Settings / Theme Studio |
| **Engine & Memory** | | | | |
| Real Vulkan Renderer Mode | `renderer` | `RendererMode` | `VulkanCompatibility` | Settings / Engine & Memory |
| Display Render Scale | `render_scale` | `RenderScale` | `Native` (100%) | Settings / Engine & Memory |
| Display Refresh Rate | `device.display_hz` | `u32` | `0` (System Auto) | Settings / Engine & Memory |
| Display Aspect Ratio | `screen.aspect` | `Aspect` | `Native` | Settings / Engine & Memory |
| Screen Orientation | `orientation` | `Orientation` | `Landscape` (Sensor) | Settings / Engine & Memory |
| Immersive Fullscreen | `screen.immersive` | `bool` | `true` | Settings / Engine & Memory |
| Avoid Display Cutout / Notch | `screen.avoid_cutout` | `bool` | `true` | Settings / Engine & Memory |
| Simulation Distance | `options.simulation_distance` | `u32` | `10` chunks | Settings / Engine & Memory |
| Render Distance | `options.render_distance` | `u32` | `12` chunks | Settings / Engine & Memory |
| Framerate Limit | `options.max_fps` | `u32` | `120` fps | Settings / Engine & Memory |
| Graphics Quality Preset | `options.graphics_preset` | `String` | `"fancy"` | Settings / Engine & Memory |
| Vertical Sync (VSync) | `options.vsync` | `bool` | `true` | Settings / Engine & Memory |
| Menu Backdrop Blur | `options.menu_blur` | `u32` | `2` | Settings / Engine & Memory |
| Worker Thread Limit | `worker_limit` | `u32` | `0` (Auto CPU detect) | Settings / Engine & Memory |
| Cache Budget (MiB) | `cache_budget_mib` | `u32` | `512` | Settings / Engine & Memory |
| **Downloads & Keep-Alive** | | | | |
| Background Keep-Alive Service | `keepalive_enabled` | `bool` | `true` | Settings / Downloads & Keep-Alive |
| Complex Voice Chat (ported by Asano Saryu) | `svc_enabled` | `bool` | `true` | Settings / Downloads & Keep-Alive |
| Complex Voice Chat RNNoise Suppression | `svc_rnnoise` | `bool` | `true` | Settings / Downloads & Keep-Alive |
| Multi-Thread Chunk Upload | `fast_chunk_upload` | `bool` | `true` | Settings / Downloads & Keep-Alive |
| **Controls** | | | | |
| Control Style | `touch.control_style` | `ControlStyle` | `GoyControl` | Settings / Controls |
| Virtual Mouse Enabled | `touch.virtual_mouse_enabled` | `bool` | `false` | Settings / Controls |
| Virtual Mouse Pointer Speed | `touch.virtual_mouse_speed` | `f32` | `1.0` | Settings / Controls (Floating Menu) |
| Gyro Aiming | `touch.gyro_enabled` | `bool` | `false` | Settings / Controls |
| Gyro Aim Sensitivity | `touch.gyro_sensitivity` | `f32` | `1.0` | Settings / Controls |
| Touch Haptic Vibration | `touch.vibrate` | `bool` | `true` | Settings / Controls |
| Auto-Jump | `settings.auto_jump` | `bool` | `true` | Settings / Controls |
| Toggle Sprint | `touch.sprint_toggle` | `bool` | `true` | Settings / Controls |
| Split Touch Controls | `touch.split_touch` | `bool` | `true` | Settings / Controls |
| Joystick Movement Zone | `touch.dynamic_joystick` | `bool` | `true` (Follow Finger) | Settings / Controls |
| Touch Button Scale | `touch.button_scale` | `f32` | `1.0` | Settings / Controls |
| Open Touch Control Editor | N/A (Action) | `Action::OpenControlEditor` | N/A | Settings / Controls |
| Keypad Controls | `keypad_controls` | `bool` | `false` | Settings / Controls |
| Input Host Mode | `input_host` | `InputHost` | `TouchOnly` | Settings / Controls |
| **Account** | | | | |
| Microsoft Account Login / Out | `microsoft_client_id` | OAuth / Token | Unlinked | Settings / Account |
| Player Username | `name` | `String` | `"Steve"` | Settings / Account |
| Active Profile Number | `profile` | `u32` | `0` | Settings / Account |
| Custom Profile Name | `custom_profile_name` | `String` | `""` | Settings / Account |
| Custom Profile Description | `custom_profile_desc` | `String` | `""` | Settings / Account |
| Last Name Changed Timestamp | `last_name_changed` | `String` | `""` | Settings / Account |
| Server Connection Address | `server` | `String` | `""` | Settings / Account |
| Connection Proxy Address | `proxy` | `Option<String>` | `None` | Settings / Account |
| **Storage** | | | | |
| Export Configuration JSON | N/A (Action) | `Action::ExportAllSettings` | N/A | Settings / Storage |
| Import Configuration JSON | N/A (Action) | `Action::ImportAllSettings` | N/A | Settings / Storage |
| Choose Background Media File | N/A (Action) | `Action::ChooseImportBackground`| N/A | Settings / Storage |
| Export Minecraft Worlds (SAF) | N/A (Action) | `Action::WorldFiles` | N/A | Settings / Storage |
| Open Game Directory (SAF) | N/A (Action) | `Action::GameFiles` | N/A | Settings / Storage |
| Cache Budget (MiB) | `cache_budget_mib` | `u32` | `512` | Settings / Storage |
| **Experimental** | | | | |
| Complex Voice Chat (ported by Asano Saryu) | `svc_enabled` | `bool` | `true` | Settings / Experimental |
| Complex Voice Chat RNNoise Noise Filter | `svc_rnnoise` | `bool` | `true` | Settings / Experimental |
| Zero Latency Touch Pipeline | `zero_latency_touch` | `bool` | `true` | Settings / Experimental |
| Fast Async Chunk Upload | `fast_chunk_upload` | `bool` | `true` | Settings / Experimental |
| Smart Hollow Leaf Culling | `experimental_leaf_culling`| `bool` | `false` | Settings / Experimental |
| Super Performance Profile | `super_perf_mode` | `bool` | `false` | Settings / Experimental |
| Battery Saver Profile | `battery_perf_mode` | `bool` | `false` | Settings / Experimental |
| Uncapped Max FPS Mode | `max_fps_mode` | `bool` | `false` | Settings / Experimental |
| Record GPU Performance Data | `record_performance` | `bool` | `false` | Settings / Experimental |
| **About & Diagnostics** | | | | |
| Horizon Android Version | N/A (Runtime) | `crate::backend::VERSION` | `26.3-android` | Settings / About |
| Target Minecraft Specification | N/A (Constant) | Constant | `Java 26.3` | Settings / About |
| Native Engine Stack | N/A (Constant) | Constant | `Rust • wgpu Vulkan` | Settings / About |
| GitHub Repository Link | N/A (Action) | `Action::OpenUrl` | `github.com/...` | Settings / About |
| Open Diagnostics Summary Modal | N/A (Action) | `Action::SettingsCat` | `Diagnostics` | Settings / About |
| System Diagnostics Data View | N/A (Modal) | System Telemetry | Real-time | Settings / Diagnostics |
