# SVC (Simple Voice Chat) Port Blueprint: Horizon Ecosystem (Desktop & Android)

Dokumen ini adalah cetak biru arsitektur aktual untuk port **Complex Voice Chat v2.0.0 (`voicechat_engine`)** sebagai **voice chat bawaan (*built-in*)** di dalam ekosistem **Horizon Android (`horizon-client` / `horizon-client-render` / `voicechat-engine`)**, beserta pembandingan dengan implementasi legacy (`svc_legacy`) yang dipertahankan sebagai fitur opsional untuk perbandingan A/B.

---

## 1. Peta Arsitektur Built-in `voicechat_engine` vs Legacy (`svc_legacy`)

Secara default (`#[cfg(not(feature = "svc_legacy"))]`), klien menjalankan engine **Complex Voice Chat v2.0.0** (`crates/voicechat-engine`). Implementasi lama (`crates/horizon-client/src/voice/` + `crates/horizon-client/src/game/voice_ui.rs`) tetap tersedia di balik Cargo feature flag `--features svc_legacy`.

| File / Crate | Tanggung Jawab Utama |
|---|---|
| `crates/voicechat-engine/src/lib.rs` | API publik crate `voicechat_engine` v2.0.0 (`join`, `poll_handshake_retry`, `payload`, `update_listener`, `set_settings`, `set_platform_hooks`, dll.). |
| `crates/voicechat-engine/build.rs` & `data/opus/` | Kompilasi statis **bundled `libopus 1.5.2`** (137 file C) via crate `cc` dengan `-DOPUS_BUILD`, `-DUSE_ALLOCA`, `-DENABLE_HARDENING`, tanpa ketergantungan `cmake`, `autotools`, atau `pkg-config` host. |
| `crates/voicechat-engine/src/proto.rs` | Codec wire protocol Simple Voice Chat 2.6.x (`COMPATIBILITY_VERSIONS = [20, 21, 19, 18, 22]`), AES-128-GCM (`ring::aead::AES_128_GCM`), dukungan `AuthenticateAck` kosong (v18–v20) maupun `AuthenticateAckNonce([u8; 16])` + `ConnectionCheckNonce([u8; 16])` (v21+). |
| `crates/voicechat-engine/src/client.rs` | State mesin koneksi (`Off`, `Waiting`, `Connecting`, `Connected`, `Lost`), retry handshake otomatis tiap 4 detik lintas versi `[20, 21, 19, 18, 22]`, thread `voicechat-connect`, `voicechat-receive`, `voicechat-auth`, dan `voicechat-mic`. |
| `crates/voicechat-engine/src/device.rs` | Thread `voicechat-devices` (`cpal` 0.16), `InResampler` (linear interpolation ke 48 kHz mono 960 sampel), `OutResampler` dengan **non-blocking `mixer.try_lock()`** pada callback audio output agar thread Oboe/AAudio real-time tidak pernah terblokir mutex, serta hook platform (`ensure_permission`, `on_mic_open`, `on_mic_close`). |
| `crates/voicechat-engine/src/mixer.rs` | Per-channel stateful Opus decoder, jitter buffer (`START_AFTER = 60 ms`, `MAX_BUFFER = 500 ms`), Packet Loss Concealment (`MAX_CONCEAL = 4` frame), 3D constant-power stereo panning, ekstraksi referensi mono speaker untuk `mic_filter`, dan `tanh` soft limiter. |
| `crates/voicechat-engine/src/mic_filter.rs` | **4-Layer Android Microphone Conditioning**: (1) Smart half-duplex ducking gate (+12 dB threshold & -12 dB atenuasi saat suara remote aktif), (2) Session-scoped Android AEC/NS hook tanpa mengubah `AudioManager` mode global, (3) 64-tap NLMS adaptive echo suppressor dengan double-talk detector, dan (4) Post-AEC RNNoise (`nnnoiseless`) tanpa pre-gain clipping + VAD probability gate + 500 ms hangover hold + post-filter gain cap (maks 2.0x) & `tanh` soft limiter. |
| `crates/horizon-client-render/src/voicechat_menu.rs` | UI 5-tab lengkap (**Players**, **Groups**, **Audio**, **Talking**, **Customize**) yang dirender pada `OptionsUi` (`Page::Credits`) saat tombol `V` atau tombol sentuh `[V]` ditekan di dalam dunia. |
| `crates/horizon-client/src/game/voicechat.rs` | Glue game loop (`tick`, `frame`, `key`, `hud`, `touch_hud`, `sync_menu`), resolusi posisi entitas 2-tahap (UUID langsung + fallback nama pemain di `tab_players`/`players` untuk server offline-mode/Velocity/BungeeCord), tombol sentuh HUD Android (`[V]`, `[Mute]`, `[PTT]`), dan integrasi sakelar Experimental launcher. |
| `crates/horizon-client/src/android/platform.rs` | `ensure_microphone_permission()` (JNI `RECORD_AUDIO` aman lintas thread via `VM_PTR`/`ACTIVITY_PTR`), `on_voice_mic_open()`, dan `on_voice_mic_close()` (diagnostik `AudioManager` satu kali per sesi + pengikatan `AcousticEchoCanceler`/`NoiseSuppressor` pada `audioSessionId` rekaman aktif dengan menjaga `MODE_NORMAL`). |
| `crates/horizon-client/src/android/catalog.rs` & `launcher_pages.rs` | Sakelar **Built-in Simple Voice Chat (SVC)** (`svc_enabled`) dan **SVC RNNoise & Echo Suppression** (`svc_rnnoise`) di halaman `SettingsCategory::Experimental`, disimpan sekali saat diubah dan disinkronkan ke atomik `android_runtime.rs` tanpa operasi I/O disk di fungsi render. |

---

## 2. Solusi Masalah Audio Spesifik Perangkat Android

### 2.1. Mengapa Speaker Terdengar Pelan / Bisu & Solusinya
1. **Mutex Contention di Callback Audio Output (`OutResampler::fill`)**:
   - Pada kode desktop asli, callback output `cpal` memanggil `self.mixer.lock()`. Ketika thread UDP (`voicechat-receive`) atau thread game (`update_listener`) memegang lock `Mixer`, thread real-time Oboe/AAudio mengalami *priority inversion* dan *underrun*.
   - **Solusi**: `OutResampler::fill` di `crates/voicechat-engine/src/device.rs` menggunakan `self.mixer.try_lock()`. Jika lock sedang dipakai sesaat oleh thread lain, callback langsung mengisi `0.0` untuk slice kecil tersebut tanpa pernah memblokir thread audio real-time.
2. **Mode Audio Global Android (`MODE_NORMAL` vs `MODE_IN_COMMUNICATION`)**:
   - Mengubah `AudioManager` ke `MODE_IN_COMMUNICATION` pada banyak perangkat Android memindahkan output ke *earpiece* telepon (`STREAM_VOICE_CALL`) dan menurunkan volume game secara drastis.
   - **Solusi**: `platform::on_voice_mic_open()` mempertahankan `AudioManager` pada `MODE_NORMAL (0)` sehingga output tetap melalui loudspeaker/headset media (`STREAM_MUSIC`), serta menaikkan default `Settings.volume` menjadi `2.0` (200%).

### 2.2. Mengapa Suara Sendiri Bocor (Echo) & RNNoise Terdengar Terdistorsi — Arsitektur 4 Lapisan (`mic_filter.rs`)
Pada implementasi lama (`svc_legacy`), sinyal mikrofon langsung dikalikan `15.0 * 32767.0` sebelum masuk ke `nnnoiseless::DenoiseState::process_frame`. Pre-gain 15x tersebut menyebabkan *hard clipping* pada input RNNoise dan membuat bocoran suara speaker HP dianggap sebagai ucapan manusia (menghasilkan suara robotik/pecah).

Pada `crates/voicechat-engine/src/mic_filter.rs`, pemrosesan mikrofon disusun dalam 4 lapisan berurutan:
1. **Lapisan 1 — Smart Half-Duplex Ducking Gate**:
   - `Mixer::render` mengekstrak sinyal mono suara pemain lain (sebelum *local mic test* dicampur) ke `mic_filter::push_speaker_reference()`.
   - Saat suara pemain lain aktif (`remote_rms > 0.003` + hangover 120 ms untuk ekor gema ruangan), ambang batas VAD efektif dinaikkan sebesar **`+12.0 dB`**, dan sinyal mikrofon diatenuasi **`-12 dB` (`0.25x`)** kecuali suara lokal pengguna benar-benar melampaui ambang *breakout* (`raw_rms >= (remote_rms * 2.5).max(0.04)`).
2. **Lapisan 2 — Session-Scoped Android Hardware AEC/NS (`platform.rs`)**:
   - Saat mikrofon dibuka, `on_voice_mic_open()` membaca `AudioManager.getActiveRecordingConfigurations()`, mengambil `getClientAudioSessionId()`, dan mengikat `AcousticEchoCanceler.create(sessionId)` serta `NoiseSuppressor.create(sessionId)` (disimpan sebagai `GlobalRef` selama sesi mikrofon terbuka dan dilepas saat `on_voice_mic_close()`) **tanpa** mengubah mode global `AudioManager`.
3. **Lapisan 3 — Lightweight 64-Tap NLMS Adaptive Echo Suppressor**:
   - Filter adaptif *Normalized Least Mean Squares* 64-tap di domain waktu mengurangi komponen gema linier menggunakan referensi mono dari `Mixer::render`, dilengkapi *double-talk detector* (`mic_energy <= ref_energy * 2.25`) agar bobot filter tidak rusak saat pengguna berbicara bersamaan.
4. **Lapisan 4 — Post-AEC RNNoise Tanpa Pre-Gain Clipping + Soft Limiter**:
   - `DenoiseState::process_frame` dijalankan **setelah** ducking dan NLMS, pada 2x sub-frame 480 sampel (48 kHz) dengan penskalaan murni `sample.clamp(-1.0, 1.0) * 32767.0` (tanpa pre-gain 15x).
   - Keputusan bicara menggabungkan level `dBFS > effective_threshold` dan probabilitas VAD RNNoise (`vad_prob >= 0.45` saat normal, `>= 0.70` saat speaker berbunyi) dengan **hangover hold 500 ms** (`25` frame).
   - Gain mikrofon pengguna diterapkan **setelah** RNNoise (dibatasi maksimum `2.0x` saat RNNoise aktif) diikuti pembatas halus `tanh()`.
