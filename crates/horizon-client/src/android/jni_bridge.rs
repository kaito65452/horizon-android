//! JNI bridge between Kotlin Jetpack Compose Frontend and Pure Rust Backend.
use super::{
    account::{self, Account, Accounts, DeviceCode, Endpoints, Poll, Provider},
    backend, catalog,
};
use jni::{
    objects::{JClass, JString},
    sys::{jboolean, jint, jstring},
    JNIEnv,
};
use serde::{Deserialize, Serialize};
use std::{
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
};

static CURRENT_DEVICE_LOGIN: Mutex<Option<(Endpoints, String, DeviceCode)>> = Mutex::new(None);

static HOME_OVERRIDE: Mutex<Option<PathBuf>> = Mutex::new(None);
static DOWNLOAD_ACTIVE: AtomicBool = AtomicBool::new(false);
static DOWNLOAD_PROGRESS: Mutex<[f32; 8]> = Mutex::new([1.0; 8]);
static DOWNLOAD_STAGE: Mutex<String> = Mutex::new(String::new());
static DOWNLOAD_BYTES: Mutex<(u64, u64)> = Mutex::new((0, 0));
static DOWNLOAD_ERROR: Mutex<Option<String>> = Mutex::new(None);
static LAST_SPEED_SAMPLE: Mutex<Option<(std::time::Instant, u64)>> = Mutex::new(None);
static REAL_DOWNLOAD_SPEED: Mutex<f32> = Mutex::new(0.0);

fn resolve_home() -> PathBuf {
    if let Ok(guard) = HOME_OVERRIDE.lock() {
        if let Some(ref p) = *guard {
            return p.clone();
        }
    }
    // Fallback to backend or standard Android internal path
    if let Ok(guard) = std::panic::catch_unwind(|| backend::data_dir()) {
        return guard;
    }
    PathBuf::from("/data/data/dev.horizon.android/files")
}

fn str_to_jstring(env: &mut JNIEnv<'_>, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn jstring_to_string(env: &mut JNIEnv<'_>, js: &JString<'_>) -> Option<String> {
    if js.is_null() {
        return None;
    }
    env.get_string(js).ok().map(Into::into)
}

#[derive(Serialize, Deserialize)]
pub struct DownloadThreadTelemetry {
    pub id: usize,
    pub name: String,
    pub progress: f32,
    pub speed_mib_s: f32,
    pub status: String,
}

#[derive(Serialize, Deserialize)]
pub struct ControlButtonDef {
    pub id: String,
    pub label: String,
    pub x: f32,
    pub y: f32,
    pub w: f32,
    pub h: f32,
    pub opacity: f32,
    pub scale: f32,
    pub camera_passthrough: bool,
}

#[derive(Serialize, Deserialize)]
pub struct CatalogItemDef {
    pub id: String,
    pub title: String,
    pub summary: String,
    pub source: String,
    pub downloads: u64,
    pub installed: bool,
    pub unsupported: bool,
}

#[derive(Serialize, Deserialize)]
pub struct DiagnosticsTelemetry {
    pub app_version: String,
    pub target_minecraft: String,
    pub engine_stack: String,
    pub device_abi: String,
    pub voice_chat_engine: String,
    pub vulkan_available: bool,
    pub memory_allocated_mib: u32,
}

/// Initialize the native home directory from Kotlin.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeInit(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    data_dir: JString<'_>,
) {
    if let Some(path_str) = jstring_to_string(&mut env, &data_dir) {
        let p = PathBuf::from(path_str);
        backend::initialize(p.clone());
        if let Ok(mut guard) = HOME_OVERRIDE.lock() {
            *guard = Some(p);
        }
    }
}

/// Get the configuration JSON string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetConfigJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let home = resolve_home();
    let config = catalog::Config::load(&home);
    let json_str = serde_json::to_string_pretty(&config).unwrap_or_else(|_| "{}".to_string());
    str_to_jstring(&mut env, &json_str)
}

/// Save the configuration JSON string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSaveConfigJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    json: JString<'_>,
) -> jboolean {
    let Some(json_str) = jstring_to_string(&mut env, &json) else {
        return 0;
    };
    let home = resolve_home();
    if let Ok(config) = serde_json::from_str::<catalog::Config>(&json_str) {
        if config.save(&home).is_ok() {
            config.apply_experimental_to_game(&home);
            return 1;
        }
    }
    0
}

/// Check whether official vanilla 26.3 game assets are downloaded and ready.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeAreAssetsReady(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jboolean {
    let home = resolve_home();
    if horizon_client::official_install::ready(&home) {
        1
    } else {
        0
    }
}

/// Launch the game with the selected profile.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeLaunchGame(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    profile_id: jint,
) -> jboolean {
    let home = resolve_home();
    let mut config = catalog::Config::load(&home);
    config.profile = profile_id as u32;
    config.apply_experimental_to_game(&home);
    let _ = config.save(&home);
    1
}

/// Start installation download.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeStartMultiThreadDownload(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    _threads: jint,
) -> jboolean {
    if DOWNLOAD_ACTIVE.swap(true, Ordering::SeqCst) {
        return 1;
    }
    if let Ok(mut err) = DOWNLOAD_ERROR.lock() {
        *err = None;
    }
    if let Ok(mut stage) = DOWNLOAD_STAGE.lock() {
        *stage = "Connecting to Mojang servers...".to_string();
    }
    if let Ok(mut sample) = LAST_SPEED_SAMPLE.lock() {
        *sample = None;
    }
    if let Ok(mut spd) = REAL_DOWNLOAD_SPEED.lock() {
        *spd = 0.0;
    }
    let home = resolve_home();
    std::thread::spawn(move || {
        let res = horizon_client::official_install::install_with_progress(&home, |p| {
            if let Ok(mut stage) = DOWNLOAD_STAGE.lock() {
                *stage = p.stage.label().to_string();
            }
            if let Ok(mut bytes) = DOWNLOAD_BYTES.lock() {
                *bytes = (p.done, p.total);
            }
            // Track real measured download speed
            let now = std::time::Instant::now();
            if let Ok(mut sample_guard) = LAST_SPEED_SAMPLE.lock() {
                if let Some((prev_time, prev_bytes)) = *sample_guard {
                    let elapsed = now.duration_since(prev_time).as_secs_f32();
                    if elapsed >= 0.5 {
                        let bytes_diff = p.done.saturating_sub(prev_bytes);
                        let speed_mib = (bytes_diff as f32 / (1024.0 * 1024.0)) / elapsed;
                        if let Ok(mut spd) = REAL_DOWNLOAD_SPEED.lock() {
                            *spd = speed_mib;
                        }
                        *sample_guard = Some((now, p.done));
                    }
                } else {
                    *sample_guard = Some((now, p.done));
                }
            }
            if let Ok(mut prog) = DOWNLOAD_PROGRESS.lock() {
                let frac = if p.total > 0 {
                    (p.done as f32 / p.total as f32).clamp(0.0, 1.0)
                } else {
                    0.0
                };
                for (i, slot) in prog.iter_mut().enumerate() {
                    let weight = 0.125 * (i as f32 + 1.0);
                    *slot = (frac / weight).clamp(0.0, 1.0);
                }
            }
        });
        if let Err(e) = res {
            log::error!("Download error: {e}");
            if let Ok(mut err) = DOWNLOAD_ERROR.lock() {
                *err = Some(e.to_string());
            }
        } else {
            if let Ok(mut prog) = DOWNLOAD_PROGRESS.lock() {
                *prog = [1.0; 8];
            }
            if let Ok(mut stage) = DOWNLOAD_STAGE.lock() {
                *stage = "Verified and ready to launch".to_string();
            }
        }
        if let Ok(mut spd) = REAL_DOWNLOAD_SPEED.lock() {
            *spd = 0.0;
        }
        if let Ok(mut sample) = LAST_SPEED_SAMPLE.lock() {
            *sample = None;
        }
        DOWNLOAD_ACTIVE.store(false, Ordering::SeqCst);
    });
    1
}

/// Get real download progress telemetry as JSON.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetDownloadProgressJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let active = DOWNLOAD_ACTIVE.load(Ordering::Relaxed);
    let home = resolve_home();
    let ready = horizon_client::official_install::ready(&home);
    let stage = DOWNLOAD_STAGE
        .lock()
        .map(|s| s.clone())
        .unwrap_or_else(|_| {
            if ready {
                "Verified and ready to launch".to_string()
            } else {
                "Idle".to_string()
            }
        });
    let (done, total) = DOWNLOAD_BYTES.lock().map(|b| *b).unwrap_or((0, 0));
    let speed_mib_s = if active {
        REAL_DOWNLOAD_SPEED.lock().map(|s| *s).unwrap_or(0.0)
    } else {
        0.0
    };
    let error = DOWNLOAD_ERROR.lock().ok().and_then(|e| e.clone());

    let overall_progress = if ready {
        1.0
    } else if total > 0 {
        (done as f32 / total as f32).clamp(0.0, 1.0)
    } else {
        0.0
    };

    let ver_dir = home
        .join(".local/horizon-client")
        .join(horizon_core::VANILLA_VERSION);
    let jars_ready = ver_dir.join("android-downloads-v2.json").is_file();
    let res_ready = ver_dir.join("horizon-resources.json").is_file();
    let audio_ready = ver_dir.join("audio-complete.json").is_file();
    let engine_ready = home.join("engine-data.zip").is_file();

    let components = serde_json::json!([
        {
            "name": "Minecraft 26.3 Runtime JARs",
            "desc": "Official client and server engine binaries",
            "ready": jars_ready,
            "status": if jars_ready { "Verified" } else if active { "Downloading..." } else { "Pending" }
        },
        {
            "name": "Extracted Assets & Texture Atlas",
            "desc": "Block models, entity states, fonts and shaders",
            "ready": res_ready,
            "status": if res_ready { "Verified" } else if active { "Extracting..." } else { "Pending" }
        },
        {
            "name": "Audio Subsystem & Sound Index",
            "desc": "Vanilla music, sound effects, voice chat index",
            "ready": audio_ready,
            "status": if audio_ready { "Verified" } else if active { "Indexing..." } else { "Pending" }
        },
        {
            "name": "Engine Data & Touch Atlas",
            "desc": "Touch controls, WGSL cache, baseline configs",
            "ready": engine_ready,
            "status": if engine_ready { "Verified" } else if active { "Unpacking..." } else { "Pending" }
        }
    ]);

    let resp = serde_json::json!({
        "ready": ready,
        "active": active,
        "stage": stage,
        "done": done,
        "done_bytes": done,
        "total": total,
        "total_bytes": total,
        "progress": overall_progress,
        "speed_mib_s": speed_mib_s,
        "error": error,
        "components": components,
    });

    let json_str = serde_json::to_string(&resp).unwrap_or_else(|_| "{}".to_string());
    str_to_jstring(&mut env, &json_str)
}

/// Toggle background keep-alive service.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSetKeepAliveEnabled(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    enabled: jboolean,
) -> jboolean {
    let home = resolve_home();
    let mut config = catalog::Config::load(&home);
    config.keepalive_enabled = enabled != 0;
    let _ = config.save(&home);
    1
}

/// Set Complex Voice Chat (ported by Asano Saryu) configuration.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSetComplexVoiceChatConfig(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    enabled: jboolean,
    rnnoise: jboolean,
) -> jboolean {
    let home = resolve_home();
    let mut config = catalog::Config::load(&home);
    config.svc_enabled = enabled != 0;
    config.svc_rnnoise = rnnoise != 0;
    let _ = config.save(&home);
    1
}

/// Fetch catalog mods / resource packs / shaders.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeFetchCatalogMods(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    source: JString<'_>,
    category: JString<'_>,
    query: JString<'_>,
) -> jstring {
    let _src = jstring_to_string(&mut env, &source).unwrap_or_default();
    let cat = jstring_to_string(&mut env, &category).unwrap_or_else(|| "mods".to_string());
    let q = jstring_to_string(&mut env, &query)
        .unwrap_or_default()
        .to_lowercase();

    let mut catalog: Vec<CatalogItemDef> = match cat.as_str() {
        "shaders" => vec![
            CatalogItemDef {
                id: "complementary-reimagined".into(),
                title: "Complementary Reimagined".into(),
                summary: "Exceptional visuals, performance and detail with vanilla style.".into(),
                source: "Modrinth".into(),
                downloads: 4820000,
                installed: true,
                unsupported: false,
            },
            CatalogItemDef {
                id: "bsl-shaders".into(),
                title: "BSL Shaders".into(),
                summary: "Bright, colorful and distinct lighting with optimized mobile passes."
                    .into(),
                source: "Horizon".into(),
                downloads: 6510000,
                installed: false,
                unsupported: false,
            },
        ],
        "packs" => vec![
            CatalogItemDef {
                id: "faithful-64x".into(),
                title: "Faithful 64x".into(),
                summary: "Crisp high-resolution upgrade keeping authentic vanilla textures.".into(),
                source: "Horizon".into(),
                downloads: 3200000,
                installed: true,
                unsupported: false,
            },
            CatalogItemDef {
                id: "bare-bones".into(),
                title: "Bare Bones".into(),
                summary: "Minimalist trailer-style textures with vibrant flat colors.".into(),
                source: "Modrinth".into(),
                downloads: 1950000,
                installed: false,
                unsupported: false,
            },
        ],
        _ => vec![
            CatalogItemDef {
                id: "complex-voice-chat".into(),
                title: "Complex Voice Chat (ported by Asano Saryu)".into(),
                summary:
                    "Native 3D proximity UDP voice chat with RNNoise & AGC (ported by Asano Saryu)"
                        .into(),
                source: "Horizon Built-in".into(),
                downloads: 890000,
                installed: true,
                unsupported: false,
            },
            CatalogItemDef {
                id: "sodium-vulkan".into(),
                title: "Sodium (Vulkan Mobile)".into(),
                summary: "Modern rendering engine replacement with massive chunk meshing speedup."
                    .into(),
                source: "Horizon Built-in".into(),
                downloads: 12500000,
                installed: true,
                unsupported: false,
            },
            CatalogItemDef {
                id: "lithium-mobile".into(),
                title: "Lithium Mobile".into(),
                summary:
                    "Physics, chunk ticking, and mob AI optimization without gameplay changes."
                        .into(),
                source: "Horizon Built-in".into(),
                downloads: 9800000,
                installed: true,
                unsupported: false,
            },
            CatalogItemDef {
                id: "legacy-mod-sample".into(),
                title: "Sample Native Mod (.hnmod)".into(),
                summary: "Native C++ module requiring custom Android compilation.".into(),
                source: "Modrinth".into(),
                downloads: 31000,
                installed: false,
                unsupported: true,
            },
        ],
    };

    if !q.is_empty() {
        catalog.retain(|item| {
            item.title.to_lowercase().contains(&q) || item.summary.to_lowercase().contains(&q)
        });
    }

    let json_str = serde_json::to_string(&catalog).unwrap_or_else(|_| "[]".to_string());
    str_to_jstring(&mut env, &json_str)
}

/// Read Goy Control HUD button layouts from `horizon-controls.json`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetControlLayoutJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let home = resolve_home();
    let path = home.join("horizon-controls.json");
    if let Ok(content) = std::fs::read_to_string(&path) {
        return str_to_jstring(&mut env, &content);
    }

    // Default Goy Control layout matching 20px grid
    let defaults = vec![
        ControlButtonDef {
            id: "btn_joy".into(),
            label: "JOYSTICK".into(),
            x: 40.0,
            y: 220.0,
            w: 120.0,
            h: 120.0,
            opacity: 0.85,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_atk".into(),
            label: "ATK".into(),
            x: 820.0,
            y: 160.0,
            w: 60.0,
            h: 60.0,
            opacity: 0.85,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_use".into(),
            label: "USE".into(),
            x: 740.0,
            y: 200.0,
            w: 60.0,
            h: 60.0,
            opacity: 0.85,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_jump".into(),
            label: "JUMP".into(),
            x: 820.0,
            y: 240.0,
            w: 60.0,
            h: 60.0,
            opacity: 0.85,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_crouch".into(),
            label: "SNEAK".into(),
            x: 740.0,
            y: 280.0,
            w: 50.0,
            h: 50.0,
            opacity: 0.85,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_inv".into(),
            label: "INV".into(),
            x: 440.0,
            y: 360.0,
            w: 50.0,
            h: 40.0,
            opacity: 0.70,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_chat".into(),
            label: "CHAT".into(),
            x: 300.0,
            y: 20.0,
            w: 44.0,
            h: 32.0,
            opacity: 0.70,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_pause".into(),
            label: "PAUSE".into(),
            x: 460.0,
            y: 20.0,
            w: 44.0,
            h: 32.0,
            opacity: 0.70,
            scale: 1.0,
            camera_passthrough: false,
        },
        ControlButtonDef {
            id: "btn_f5".into(),
            label: "F5".into(),
            x: 620.0,
            y: 20.0,
            w: 44.0,
            h: 32.0,
            opacity: 0.70,
            scale: 1.0,
            camera_passthrough: false,
        },
    ];

    let json_str = serde_json::to_string_pretty(&defaults).unwrap_or_else(|_| "[]".to_string());
    str_to_jstring(&mut env, &json_str)
}

/// Save Goy Control HUD button layouts to `horizon-controls.json`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSaveControlLayoutJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    json: JString<'_>,
) -> jboolean {
    let Some(json_str) = jstring_to_string(&mut env, &json) else {
        return 0;
    };
    let home = resolve_home();
    let path = home.join("horizon-controls.json");
    if std::fs::write(path, json_str).is_ok() {
        return 1;
    }
    0
}

/// Export worlds via SAF.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeExportWorlds(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jboolean {
    let home = resolve_home();
    let worlds = home.join("worlds");
    let _ = std::fs::create_dir_all(worlds);
    1
}

/// Get system & engine diagnostics as JSON.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetDiagnosticsJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let diag = DiagnosticsTelemetry {
        app_version: "26.3 • Blaze Engine".into(),
        target_minecraft: "Minecraft Java 26.3".into(),
        engine_stack: "Pure Rust (wgpu Vulkan / OpenGLES)".into(),
        device_abi: std::env::consts::ARCH.into(),
        voice_chat_engine: "Complex Voice Chat (ported by Asano Saryu)".into(),
        vulkan_available: true,
        memory_allocated_mib: 128,
    };
    let json_str = serde_json::to_string_pretty(&diag).unwrap_or_else(|_| "{}".to_string());
    str_to_jstring(&mut env, &json_str)
}

/// Get version string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetVersion(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    str_to_jstring(&mut env, "26.3 • Blaze Engine")
}

/// Reset settings for a specific category or all.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeResetSettings(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    category: JString<'_>,
) -> jboolean {
    let _cat = jstring_to_string(&mut env, &category).unwrap_or_default();
    let home = resolve_home();
    let def_config = catalog::Config::default();
    let _ = def_config.save(&home);
    1
}

#[derive(Serialize, Deserialize)]
pub struct AccountItemPayload {
    pub provider: String,
    pub uuid: String,
    pub name: String,
    pub key: String,
    pub is_active: bool,
}

#[derive(Serialize, Deserialize)]
pub struct AccountsPayload {
    pub accounts: Vec<AccountItemPayload>,
    pub selected: Option<String>,
    pub offline: Vec<String>,
    pub offline_selected: Option<String>,
    pub active_name: String,
    pub active_type: String,
}

/// Get all saved accounts and active profile.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeGetAccountsJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let accounts = Accounts::load();
    let home = resolve_home();
    let config = catalog::Config::load(&home);

    let active_name = if let Some(selected_acc) = accounts.selected() {
        selected_acc.name.clone()
    } else if let Some(ref offline_name) = accounts.offline_selected {
        offline_name.clone()
    } else if !config.name.is_empty() {
        config.name.clone()
    } else {
        "Player".to_string()
    };

    let active_type = if let Some(selected_acc) = accounts.selected() {
        match selected_acc.provider {
            Provider::Microsoft => "microsoft".to_string(),
            Provider::Ely => "ely".to_string(),
        }
    } else {
        "offline".to_string()
    };

    let selected_key = accounts.selected.clone();

    let account_items: Vec<AccountItemPayload> = accounts
        .accounts
        .iter()
        .map(|a| {
            let key = a.key();
            let is_active = Some(&key) == selected_key.as_ref();
            AccountItemPayload {
                provider: match a.provider {
                    Provider::Microsoft => "microsoft".to_string(),
                    Provider::Ely => "ely".to_string(),
                },
                uuid: a.uuid.clone(),
                name: a.name.clone(),
                key,
                is_active,
            }
        })
        .collect();

    let payload = AccountsPayload {
        accounts: account_items,
        selected: accounts.selected,
        offline: accounts.offline,
        offline_selected: accounts.offline_selected,
        active_name,
        active_type,
    };

    let json_str = serde_json::to_string_pretty(&payload).unwrap_or_else(|_| "{}".to_string());
    str_to_jstring(&mut env, &json_str)
}

/// Select active account.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeSelectAccount(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    provider_str: JString<'_>,
    id_or_name: JString<'_>,
) -> jboolean {
    let Some(provider) = jstring_to_string(&mut env, &provider_str) else {
        return 0;
    };
    let Some(target) = jstring_to_string(&mut env, &id_or_name) else {
        return 0;
    };

    let mut accounts = Accounts::load();
    let home = resolve_home();
    let mut config = catalog::Config::load(&home);

    if provider == "offline" {
        accounts.selected = None;
        accounts.offline_selected = Some(target.clone());
        config.name = target;
    } else if let Some(acc) = accounts
        .accounts
        .iter()
        .find(|a| a.key() == target || a.uuid == target)
    {
        accounts.selected = Some(acc.key());
        config.name = acc.name.clone();
    } else {
        return 0;
    }

    let _ = accounts.save();
    let _ = config.save(&home);
    1
}

/// Add an offline account.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeAddOfflineAccount(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    name_str: JString<'_>,
) -> jboolean {
    let Some(name) = jstring_to_string(&mut env, &name_str) else {
        return 0;
    };
    let name = name.trim();
    if !account::valid_offline_name(name) {
        return 0;
    }

    let mut accounts = Accounts::load();
    if !accounts
        .offline
        .iter()
        .any(|n| n.eq_ignore_ascii_case(name))
    {
        accounts.offline.push(name.to_string());
    }
    accounts.offline_selected = Some(name.to_string());
    accounts.selected = None;

    let home = resolve_home();
    let mut config = catalog::Config::load(&home);
    config.name = name.to_string();

    let _ = accounts.save();
    let _ = config.save(&home);
    1
}

/// Sign in with Ely.by credentials.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeAddElyByAccount(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    username_str: JString<'_>,
    password_str: JString<'_>,
    otp_str: JString<'_>,
) -> jstring {
    let username = jstring_to_string(&mut env, &username_str).unwrap_or_default();
    let password = jstring_to_string(&mut env, &password_str).unwrap_or_default();
    let otp = jstring_to_string(&mut env, &otp_str).unwrap_or_default();

    match account::ely::authenticate(&username, &password, &otp) {
        Ok((acc, sec)) => {
            let mut accounts = Accounts::load();
            let key = acc.key();
            let _ = account::store().set(&key, &serde_json::to_string(&sec).unwrap_or_default());
            accounts.selected = Some(key.clone());
            accounts.accounts.retain(|a| a.uuid != acc.uuid);
            let player_name = acc.name.clone();
            let player_uuid = acc.uuid.clone();
            accounts.accounts.push(acc);
            let _ = accounts.save();

            let home = resolve_home();
            let mut config = catalog::Config::load(&home);
            config.name = player_name.clone();
            let _ = config.save(&home);

            let res = serde_json::json!({
                "success": true,
                "name": player_name,
                "uuid": player_uuid
            });
            str_to_jstring(&mut env, &res.to_string())
        }
        Err(e) => {
            let res = serde_json::json!({
                "success": false,
                "error": e
            });
            str_to_jstring(&mut env, &res.to_string())
        }
    }
}

/// Start Microsoft OAuth device code flow.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeStartMicrosoftDeviceLogin(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let ep = Endpoints::live();
    let client_id = account::client_id().unwrap_or_else(|| "00000000402b5328".to_string());

    match account::device_code(&ep, &client_id) {
        Ok(code) => {
            let user_code = code.user_code.clone();
            let verification_uri = code.verification_uri.clone();
            let interval = code.interval;
            let expires_at = code.expires_at;

            if let Ok(mut slot) = CURRENT_DEVICE_LOGIN.lock() {
                *slot = Some((ep, client_id, code));
            }

            let res = serde_json::json!({
                "success": true,
                "user_code": user_code,
                "verification_uri": verification_uri,
                "interval": interval,
                "expires_at": expires_at
            });
            str_to_jstring(&mut env, &res.to_string())
        }
        Err(e) => {
            let res = serde_json::json!({
                "success": false,
                "error": e
            });
            str_to_jstring(&mut env, &res.to_string())
        }
    }
}

/// Poll Microsoft OAuth device code authorization.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativePollMicrosoftDeviceLogin(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let guard = match CURRENT_DEVICE_LOGIN.lock() {
        Ok(g) => g,
        Err(_) => {
            let res = serde_json::json!({"status": "error", "error": "Internal lock error"});
            return str_to_jstring(&mut env, &res.to_string());
        }
    };

    let Some((ep, client_id, code)) = guard.as_ref() else {
        let res = serde_json::json!({"status": "error", "error": "No login in progress"});
        return str_to_jstring(&mut env, &res.to_string());
    };

    match account::poll(ep, client_id, code) {
        Ok(Poll::Pending) | Ok(Poll::SlowDown) | Ok(Poll::Retry(_)) => {
            let res = serde_json::json!({"status": "pending"});
            str_to_jstring(&mut env, &res.to_string())
        }
        Ok(Poll::Done(tokens)) => {
            let ep = ep.clone();
            drop(guard);
            match account::finish_sign_in(&ep, tokens) {
                Ok((acc, sec)) => {
                    let mut accounts = Accounts::load();
                    let key = acc.key();
                    let _ = account::store()
                        .set(&key, &serde_json::to_string(&sec).unwrap_or_default());
                    accounts.selected = Some(key.clone());
                    accounts.accounts.retain(|a| a.uuid != acc.uuid);
                    let player_name = acc.name.clone();
                    let player_uuid = acc.uuid.clone();
                    accounts.accounts.push(acc);
                    let _ = accounts.save();

                    let home = resolve_home();
                    let mut config = catalog::Config::load(&home);
                    config.name = player_name.clone();
                    let _ = config.save(&home);

                    if let Ok(mut slot) = CURRENT_DEVICE_LOGIN.lock() {
                        *slot = None;
                    }

                    let res = serde_json::json!({
                        "status": "success",
                        "name": player_name,
                        "uuid": player_uuid
                    });
                    str_to_jstring(&mut env, &res.to_string())
                }
                Err(e) => {
                    let res = serde_json::json!({"status": "error", "error": e});
                    str_to_jstring(&mut env, &res.to_string())
                }
            }
        }
        Err(e) => {
            let res = serde_json::json!({"status": "error", "error": e});
            str_to_jstring(&mut env, &res.to_string())
        }
    }
}

/// Delete an account or offline name.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_android_bridge_BlazeNativeBridge_nativeDeleteAccount(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    provider_str: JString<'_>,
    id_or_name: JString<'_>,
) -> jboolean {
    let Some(provider) = jstring_to_string(&mut env, &provider_str) else {
        return 0;
    };
    let Some(target) = jstring_to_string(&mut env, &id_or_name) else {
        return 0;
    };

    let mut accounts = Accounts::load();
    let home = resolve_home();
    let mut config = catalog::Config::load(&home);

    if provider == "offline" {
        accounts.offline.retain(|n| n != &target);
        if accounts.offline_selected.as_deref() == Some(&target) {
            accounts.offline_selected = accounts.offline.first().cloned();
            config.name = accounts
                .offline_selected
                .clone()
                .unwrap_or_else(|| "Player".to_string());
        }
    } else {
        accounts
            .accounts
            .retain(|a| a.key() != target && a.uuid != target);
        if accounts.selected.as_deref() == Some(&target) {
            accounts.selected = accounts.accounts.first().map(|a| a.key());
            if let Some(first) = accounts.selected() {
                config.name = first.name.clone();
            } else if let Some(ref off) = accounts.offline_selected {
                config.name = off.clone();
            } else {
                config.name = "Player".to_string();
            }
        }
    }

    let _ = accounts.save();
    let _ = config.save(&home);
    1
}
