//! Microphone signal conditioning for mobile and desktop speakers:
//! 1. Multi-source 400 ms playback reference tracker covering both remote voice chat (`Mixer`)
//!    and game audio (`horizon-client-render::audio` music/SFX) with a 350 ms acoustic hold tail
//!    to span Android Oboe output + air + input buffer latency (60–300 ms).
//! 2. 40-tap (400 ms) non-negative delay-aligned Envelope Echo Canceler (`EnvelopeEchoCanceler`)
//!    that is immune to sample-clock drift and nonlinear micro-speaker distortion, and cannot
//!    diverge over multi-minute/hour sessions.
//! 3. Strict half-duplex echo gate (cancelling open-mic hangover on echo frames so feedback loops
//!    can never bounce) and game-music bleed rejection.
//! 4. Post-AEC RNNoise (`nnnoiseless`) neural denoising at 48 kHz without pre-gain clipping,
//!    with VAD probability gating, 300 ms hangover hold, and post-filter soft limiting.

use crate::opus::FRAME;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicU32, Ordering::Relaxed};
use std::sync::{Mutex, OnceLock};

/// 10 ms subframe size required by `nnnoiseless::DenoiseState` at 48 kHz.
pub const RNNOISE_SUBFRAME: usize = 480;
/// Number of 20 ms frames to hold voice activation open after clean speech stops (15 * 20 ms = 300 ms).
pub const HANGOVER_FRAMES: u32 = 15;
/// Extra VAD threshold required (in dB) while remote voices are playing from the speakers.
pub const DUCKING_THRESHOLD_BOOST_DB: f32 = 20.0;
/// Extra VAD threshold required (in dB) while game music/SFX is playing from the speakers.
pub const MUSIC_THRESHOLD_BOOST_DB: f32 = 12.0;
/// Attenuation factor (-28 dB) applied to the microphone while remote voices dominate.
pub const DUCKING_ATTENUATION: f32 = 0.04;
/// Attenuation factor (-18 dB) applied to the microphone while game music bleed dominates.
pub const MUSIC_DUCKING_ATTENUATION: f32 = 0.12;
/// Maximum user mic gain allowed when RNNoise is active, preventing noise-floor over-amplification.
pub const MAX_RNNOISE_GAIN: f32 = 2.0;
/// Number of 10 ms envelope history taps (40 * 10 ms = 400 ms delay coverage).
pub const ENVELOPE_TAPS: usize = 40;
/// Maximum mono reference samples buffered from the speaker mixer (100 ms at 48 kHz).
const MAX_REF_SAMPLES: usize = 4800;

struct SpeakerRefState {
    mono_ring: VecDeque<f32>,
    voice_env_history: [f32; ENVELOPE_TAPS],
    game_env_history: [f32; ENVELOPE_TAPS],
    env_pos: usize,
    voice_hold_samples: usize,
    game_hold_samples: usize,
    voice_peak_rms: f32,
    game_peak_rms: f32,
}

static SPEAKER_REF: OnceLock<Mutex<SpeakerRefState>> = OnceLock::new();
static REMOTE_RMS_BITS: AtomicU32 = AtomicU32::new(0);
static GAME_RMS_BITS: AtomicU32 = AtomicU32::new(0);
/// Hardware media volume ratio (0.0..=1.0), defaulting to 0.80 so speakerphone protection is strong.
static HW_VOLUME_SCALE_BITS: AtomicU32 = AtomicU32::new(0x3f4ccccd); // 0.80f32

fn speaker_ref() -> &'static Mutex<SpeakerRefState> {
    SPEAKER_REF.get_or_init(|| {
        Mutex::new(SpeakerRefState {
            mono_ring: VecDeque::with_capacity(MAX_REF_SAMPLES),
            voice_env_history: [0.0; ENVELOPE_TAPS],
            game_env_history: [0.0; ENVELOPE_TAPS],
            env_pos: 0,
            voice_hold_samples: 0,
            game_hold_samples: 0,
            voice_peak_rms: 0.0,
            game_peak_rms: 0.0,
        })
    })
}

/// Updates the Android hardware media volume ratio (`0.0..=1.0`, e.g. `14/15 = 0.93`)
/// so the echo gate scales its bleed estimate when the user turns up the physical speaker volume.
pub fn set_hardware_volume_scale(scale: f32) {
    let clamped = scale.clamp(0.15, 1.5);
    HW_VOLUME_SCALE_BITS.store(clamped.to_bits(), Relaxed);
}

/// Returns the current hardware volume scale multiplier (`0.5..=2.2`).
pub fn hardware_volume_multiplier() -> f32 {
    let ratio = f32::from_bits(HW_VOLUME_SCALE_BITS.load(Relaxed)).clamp(0.15, 1.5);
    // Above 50% media volume on phone loudspeakers, acoustic coupling into the bottom mic rises steeply.
    (0.45 + ratio * 1.45).clamp(0.5, 2.2)
}

/// Called from `Mixer::render` with the interleaved stereo output of remote voices
/// (before local mic-test loopback is mixed in). Never blocks the audio output callback.
pub fn push_speaker_reference(stereo: &[f32]) {
    let frames = stereo.len() / 2;
    if frames == 0 {
        return;
    }
    let mut sum_sq = 0.0f64;
    for i in 0..frames {
        let mono = (stereo[2 * i] + stereo[2 * i + 1]) * 0.5;
        sum_sq += (mono as f64) * (mono as f64);
    }
    let block_rms = (sum_sq / frames as f64).sqrt() as f32;

    let Ok(mut state) = speaker_ref().try_lock() else {
        return;
    };
    let pos = state.env_pos;
    state.voice_env_history[pos] = block_rms;
    state.env_pos = (pos + 1) % ENVELOPE_TAPS;

    let history_peak = state
        .voice_env_history
        .iter()
        .copied()
        .fold(0.0f32, f32::max);
    if block_rms > 0.0025 {
        // Hold remote-talking state for 350 ms (16,800 samples at 48 kHz) to cover Android
        // Oboe output buffer + room air path + Oboe input buffer latency.
        state.voice_hold_samples = 16_800;
        state.voice_peak_rms = history_peak.max(block_rms);
    } else {
        state.voice_hold_samples = state.voice_hold_samples.saturating_sub(frames);
        if state.voice_hold_samples > 0 {
            state.voice_peak_rms = history_peak.max(state.voice_peak_rms * 0.96);
        } else {
            state.voice_peak_rms *= 0.85;
        }
    }
    let effective_rms = if state.voice_hold_samples > 0 {
        state.voice_peak_rms.max(0.004)
    } else {
        state.voice_peak_rms
    };
    REMOTE_RMS_BITS.store(effective_rms.to_bits(), Relaxed);

    for i in 0..frames {
        let mono = (stereo[2 * i] + stereo[2 * i + 1]) * 0.5;
        if state.mono_ring.len() >= MAX_REF_SAMPLES {
            state.mono_ring.pop_front();
        }
        state.mono_ring.push_back(mono);
    }
}

/// Called from `horizon_client_render::audio` with the mixed game audio output buffer
/// (Minecraft background music, ambient sounds, block/entity SFX) so the microphone filter
/// suppresses game music bleed into the phone's microphone.
pub fn push_game_audio_reference(samples: &[f32], channels: usize, sample_rate: u32) {
    let channels = channels.max(1);
    let frames = samples.len() / channels;
    if frames == 0 {
        return;
    }
    let mut sum_sq = 0.0f64;
    for chunk in samples.chunks_exact(channels) {
        let mono: f32 = chunk.iter().copied().sum::<f32>() / channels as f32;
        sum_sq += (mono as f64) * (mono as f64);
    }
    let block_rms = (sum_sq / frames as f64).sqrt() as f32;

    let Ok(mut state) = speaker_ref().try_lock() else {
        return;
    };
    // Record into the current envelope slot without advancing `env_pos` twice if voice is also running,
    // or advance if voice mixer is idle.
    let slot = if state.voice_hold_samples > 0 {
        (state.env_pos + ENVELOPE_TAPS - 1) % ENVELOPE_TAPS
    } else {
        let p = state.env_pos;
        state.voice_env_history[p] *= 0.90;
        state.env_pos = (p + 1) % ENVELOPE_TAPS;
        p
    };
    state.game_env_history[slot] = block_rms;

    let history_peak = state
        .game_env_history
        .iter()
        .copied()
        .fold(0.0f32, f32::max);
    let hold_250ms = (sample_rate.max(8_000) as usize) / 4;
    if block_rms > 0.0025 {
        state.game_hold_samples = hold_250ms;
        state.game_peak_rms = history_peak.max(block_rms);
    } else {
        state.game_hold_samples = state.game_hold_samples.saturating_sub(frames);
        if state.game_hold_samples > 0 {
            state.game_peak_rms = history_peak.max(state.game_peak_rms * 0.96);
        } else {
            state.game_peak_rms *= 0.85;
        }
    }
    let effective_game_rms = if state.game_hold_samples > 0 {
        state.game_peak_rms.max(0.0035)
    } else {
        state.game_peak_rms
    };
    GAME_RMS_BITS.store(effective_game_rms.to_bits(), Relaxed);
}

/// Pulls up to `len` mono reference samples and the current remote playback RMS.
pub fn pull_speaker_reference(len: usize) -> (Vec<f32>, f32) {
    let rms = f32::from_bits(REMOTE_RMS_BITS.load(Relaxed));
    let mut ref_buf = vec![0.0f32; len];
    if let Ok(mut state) = speaker_ref().try_lock() {
        let avail = state.mono_ring.len().min(len);
        let start = len - avail;
        for dst in &mut ref_buf[start..] {
            *dst = state.mono_ring.pop_front().unwrap_or(0.0);
        }
    }
    (ref_buf, rms)
}

/// Returns the latest game audio (music/SFX) playback RMS and the 40-tap (400 ms) combined
/// speaker envelope history ordered from newest to oldest.
fn pull_envelope_history() -> (f32, [f32; ENVELOPE_TAPS]) {
    let game_rms = f32::from_bits(GAME_RMS_BITS.load(Relaxed));
    let mut hist = [0.0f32; ENVELOPE_TAPS];
    if let Ok(state) = speaker_ref().try_lock() {
        for k in 0..ENVELOPE_TAPS {
            let idx = (state.env_pos + ENVELOPE_TAPS - 1 - k) % ENVELOPE_TAPS;
            let v = state.voice_env_history[idx];
            let g = state.game_env_history[idx];
            hist[k] = (v * v + g * g).sqrt();
        }
    }
    (game_rms, hist)
}

/// 40-tap (400 ms) non-negative Delay-Aligned Envelope Echo Canceler.
/// Operates on 10–20 ms energy envelopes rather than raw 48 kHz carrier phase, making it
/// immune to Android output/input stream clock drift, 60–300 ms buffer latency, and
/// nonlinear micro-speaker harmonic distortion, while guaranteed never to diverge.
pub struct EnvelopeEchoCanceler {
    weights: [f32; ENVELOPE_TAPS],
    local_history: [f32; ENVELOPE_TAPS],
    hist_pos: usize,
    coupling_floor: f32,
}

impl Default for EnvelopeEchoCanceler {
    fn default() -> Self {
        let mut weights = [0.0; ENVELOPE_TAPS];
        // Pre-seed typical Android latency taps (60 ms to 180 ms, taps 6..18) so echo
        // suppression is active from the very first syllable after joining a server.
        for w in &mut weights[6..18] {
            *w = 0.04;
        }
        Self {
            weights,
            local_history: [0.0; ENVELOPE_TAPS],
            hist_pos: 0,
            coupling_floor: 0.45,
        }
    }
}

impl EnvelopeEchoCanceler {
    pub fn reset(&mut self) {
        *self = Self::default();
    }

    /// Updates the delay-aligned envelope model and returns `(estimated_echo_rms, suppression_gain)`.
    pub fn estimate_and_suppress(
        &mut self,
        mic_rms: f32,
        frame_ref_rms: f32,
        global_hist: &[f32; ENVELOPE_TAPS],
        hw_mult: f32,
    ) -> (f32, f32) {
        self.local_history[self.hist_pos] = frame_ref_rms;
        self.hist_pos = (self.hist_pos + 1) % ENVELOPE_TAPS;

        let mut taps = [0.0f32; ENVELOPE_TAPS];
        let mut norm = 1e-4f32;
        let mut peak_tap = 0.0f32;
        for (k, tap) in taps.iter_mut().enumerate() {
            let local_idx = (self.hist_pos + ENVELOPE_TAPS - 1 - k) % ENVELOPE_TAPS;
            let r = self.local_history[local_idx].max(global_hist[k]) * hw_mult;
            *tap = r;
            norm += r * r;
            if r > peak_tap {
                peak_tap = r;
            }
        }

        let mut echo_est = 0.0f32;
        for (w, &r) in self.weights.iter().zip(&taps) {
            echo_est += *w * r;
        }

        // Adapt non-negative tap weights when speaker reference is present and mic is not
        // overwhelmingly louder than the speaker (double-talk guard).
        if peak_tap > 0.002 && mic_rms <= peak_tap * 2.5 {
            let err = mic_rms - echo_est;
            let step = (0.08 * err) / norm;
            let mut weight_sum = 0.0f32;
            for (w, &r) in self.weights.iter_mut().zip(&taps) {
                // Leaky retention prevents weights from growing unboundedly over long sessions.
                let updated = (*w * 0.9992 + step * r).clamp(0.0, 1.2);
                *w = if updated.is_finite() { updated } else { 0.0 };
                weight_sum += *w;
            }
            // Bound total coupling gain to [0.20, 2.50] so the filter never goes dead or explodes.
            if weight_sum > 2.50 {
                let scale = 2.50 / weight_sum;
                for w in &mut self.weights {
                    *w *= scale;
                }
            }
            let obs_ratio = (mic_rms / peak_tap).clamp(0.15, 1.8);
            self.coupling_floor = (self.coupling_floor * 0.92 + obs_ratio * 0.08).clamp(0.30, 1.60);
        }

        let guaranteed_floor = peak_tap * self.coupling_floor;
        let effective_echo = echo_est.max(guaranteed_floor);
        let gain = if mic_rms > 1e-5 && effective_echo > 0.001 {
            (1.0 - (1.45 * effective_echo) / mic_rms).clamp(0.04, 1.0)
        } else {
            1.0
        };
        (effective_echo, gain)
    }
}

/// Output of conditioning a single 20 ms (960-sample) 48 kHz microphone frame.
#[derive(Clone, Debug)]
pub struct FilterOutput {
    pub samples: Vec<f32>,
    pub level_db: f32,
    pub vad_prob: f32,
    pub speaking: bool,
    pub ducked: bool,
}

/// Stateful microphone conditioner running on the `voicechat-mic` thread.
pub struct MicFilter {
    #[cfg(feature = "rnnoise")]
    denoiser: Box<nnnoiseless::DenoiseState<'static>>,
    aec: EnvelopeEchoCanceler,
    hangover_left: u32,
}

impl Default for MicFilter {
    fn default() -> Self {
        Self::new()
    }
}

impl MicFilter {
    pub fn new() -> Self {
        Self {
            #[cfg(feature = "rnnoise")]
            denoiser: nnnoiseless::DenoiseState::new(),
            aec: EnvelopeEchoCanceler::default(),
            hangover_left: 0,
        }
    }

    /// Processes a 960-sample 48 kHz mono frame with:
    /// 1. 400 ms delay-aligned envelope echo estimation against both remote voices and game music/SFX.
    /// 2. Strict half-duplex ducking gate (with hangover cancellation on echo frames so feedback
    ///    loops never bounce) and game-music bleed suppression.
    /// 3. Post-AEC RNNoise (if `rnnoise_enabled`) without pre-gain clipping.
    /// 4. Post-filter user gain (capped at 2.0x when RNNoise is active) + `tanh` soft limiter.
    /// 5. Combined dBFS + RNNoise VAD probability check with 300 ms hangover hold.
    pub fn process_frame(
        &mut self,
        raw_frame: &[f32],
        speaker_ref: &[f32],
        remote_rms: f32,
        user_gain: f32,
        threshold_db: f32,
        rnnoise_enabled: bool,
    ) -> FilterOutput {
        let mut working: Vec<f32> = raw_frame.iter().map(|&x| x.clamp(-1.0, 1.0)).collect();
        let len = working.len().max(1);

        let raw_rms =
            (working.iter().map(|&v| (v as f64).powi(2)).sum::<f64>() / len as f64).sqrt() as f32;
        let ref_frame_rms = if speaker_ref.is_empty() {
            0.0
        } else {
            (speaker_ref.iter().map(|&v| (v as f64).powi(2)).sum::<f64>()
                / speaker_ref.len() as f64)
                .sqrt() as f32
        };

        let (game_rms, global_hist) = pull_envelope_history();
        let hw_mult = hardware_volume_multiplier();
        let combined_ref_rms = (remote_rms * remote_rms + game_rms * game_rms)
            .sqrt()
            .max(ref_frame_rms);

        let (estimated_echo_rms, aec_gain) =
            self.aec
                .estimate_and_suppress(raw_rms, combined_ref_rms, &global_hist, hw_mult);

        let remote_talking =
            remote_rms > 0.0025 || ref_frame_rms > 0.0025 || estimated_echo_rms > 0.004;
        let game_playing = game_rms > 0.0025;

        // Layer 1 & 2: Strict half-duplex echo gate for remote voice + game music bleed suppression.
        // While remote voice is active (including the 350 ms buffer latency hold), local speech
        // must clearly exceed both 3.0x the estimated echo envelope and an absolute 0.14 RMS floor.
        let voice_breakout_rms = (estimated_echo_rms * 3.0)
            .max(remote_rms * hw_mult * 2.8)
            .max(0.14);
        let music_breakout_rms = (estimated_echo_rms * 2.4)
            .max(game_rms * hw_mult * 2.2)
            .max(0.06);

        let voice_ducked = remote_talking && raw_rms < voice_breakout_rms;
        let music_ducked = !voice_ducked && game_playing && raw_rms < music_breakout_rms;
        let ducked = voice_ducked || music_ducked;

        if voice_ducked {
            let atten = DUCKING_ATTENUATION.min(aec_gain);
            for s in &mut working {
                *s *= atten;
            }
            // Cancel any open-mic hangover immediately when speaker echo is ducked so a brief
            // spike can never hold the mic open for 300-500 ms and start a feedback bounce loop.
            self.hangover_left = 0;
        } else if music_ducked {
            let atten = MUSIC_DUCKING_ATTENUATION.min(aec_gain);
            for s in &mut working {
                *s *= atten;
            }
        } else if (remote_talking || game_playing) && aec_gain < 1.0 {
            for s in &mut working {
                *s *= aec_gain;
            }
        }

        // Layer 3: Post-AEC RNNoise without pre-gain clipping, followed by post-filter gain.
        let mut vad_prob = 1.0f32;
        #[cfg(feature = "rnnoise")]
        if rnnoise_enabled && working.len() == FRAME {
            let mut in_sub = [0.0f32; RNNOISE_SUBFRAME];
            let mut out_sub = [0.0f32; RNNOISE_SUBFRAME];

            for (dst, &src) in in_sub.iter_mut().zip(&working[..RNNOISE_SUBFRAME]) {
                *dst = src.clamp(-1.0, 1.0) * 32767.0;
            }
            let vad_0 = self.denoiser.process_frame(&mut out_sub, &in_sub);
            for (dst, &src) in working[..RNNOISE_SUBFRAME].iter_mut().zip(&out_sub) {
                *dst = (src / 32767.0).clamp(-1.0, 1.0);
            }

            for (dst, &src) in in_sub.iter_mut().zip(&working[RNNOISE_SUBFRAME..FRAME]) {
                *dst = src.clamp(-1.0, 1.0) * 32767.0;
            }
            let vad_1 = self.denoiser.process_frame(&mut out_sub, &in_sub);
            for (dst, &src) in working[RNNOISE_SUBFRAME..FRAME].iter_mut().zip(&out_sub) {
                *dst = (src / 32767.0).clamp(-1.0, 1.0);
            }

            vad_prob = vad_0.max(vad_1);
        }
        #[cfg(not(feature = "rnnoise"))]
        let _ = rnnoise_enabled;

        let effective_gain = if rnnoise_enabled {
            user_gain.clamp(0.0, MAX_RNNOISE_GAIN)
        } else {
            user_gain.clamp(0.0, 4.0)
        };
        for s in &mut working {
            *s = (*s * effective_gain).tanh();
        }

        let post_rms =
            (working.iter().map(|&v| (v as f64).powi(2)).sum::<f64>() / len as f64).sqrt();
        let level_db = if post_rms > 0.0 {
            (20.0 * post_rms.log10()) as f32
        } else {
            -100.0
        };

        let echo_floor_db = if estimated_echo_rms > 1e-4 {
            20.0 * (estimated_echo_rms * 2.2).log10()
        } else {
            -100.0
        };
        let effective_threshold = if remote_talking {
            (threshold_db + DUCKING_THRESHOLD_BOOST_DB)
                .max(echo_floor_db)
                .min(-12.0)
        } else if game_playing {
            (threshold_db + MUSIC_THRESHOLD_BOOST_DB)
                .max(echo_floor_db)
                .min(-18.0)
        } else {
            threshold_db
        };
        let min_vad = if !rnnoise_enabled {
            0.0
        } else if remote_talking {
            0.85
        } else if game_playing {
            0.82
        } else {
            0.50
        };

        if !ducked && level_db > effective_threshold && vad_prob >= min_vad {
            self.hangover_left = HANGOVER_FRAMES;
        } else if self.hangover_left > 0 {
            self.hangover_left -= 1;
        }
        let speaking = !voice_ducked && self.hangover_left > 0;

        FilterOutput {
            samples: working,
            level_db,
            vad_prob,
            speaking,
            ducked,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ducking_attenuates_bleed_when_remote_is_talking() {
        let mut filter = MicFilter::new();
        // Speaker bleed (RMS ~0.05, typical for phone loudspeaker at 10/15..14/15 volume)
        // while remote speaker is loud (RMS 0.08).
        let bleed: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.1).sin()) * 0.07)
            .collect();
        let speaker_ref: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.1).sin()) * 0.11)
            .collect();

        let out = filter.process_frame(&bleed, &speaker_ref, 0.08, 1.0, -50.0, false);
        assert!(
            out.ducked,
            "expected ducking gate to engage on speaker bleed"
        );
        assert!(
            !out.speaking,
            "expected raised ducking threshold and half-duplex gate to prevent false VAD trigger"
        );
        let out_rms = (out.samples.iter().map(|&x| x * x).sum::<f32>() / FRAME as f32).sqrt();
        let in_rms = (bleed.iter().map(|&x| x * x).sum::<f32>() / FRAME as f32).sqrt();
        assert!(
            out_rms < in_rms * 0.15,
            "expected strong attenuation (< 0.15x): in_rms={in_rms}, out_rms={out_rms}"
        );
    }

    #[test]
    fn delayed_echo_after_speaker_stops_is_still_suppressed() {
        let mut filter = MicFilter::new();
        let loud_ref: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.1).sin()) * 0.12)
            .collect();
        let silence_frame = vec![0.0f32; FRAME];

        // Frame 0: remote player speaks into speaker_ref, mic has not heard it yet (0.0).
        let _ = filter.process_frame(&silence_frame, &loud_ref, 0.09, 1.0, -50.0, false);

        // Frames 1..8 (160 ms later): remote stream is now silent (`remote_rms = 0.0`),
        // and the delayed acoustic echo (amplitude 0.06) arrives at the phone's microphone!
        for _ in 0..7 {
            let _ = filter.process_frame(&silence_frame, &[], 0.0, 1.0, -50.0, false);
        }
        let delayed_echo: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.1).sin()) * 0.06)
            .collect();
        let out = filter.process_frame(&delayed_echo, &[], 0.0, 1.0, -50.0, false);
        assert!(
            out.ducked && !out.speaking,
            "160 ms delayed speaker echo must be caught by the 400 ms envelope history without triggering VAD"
        );
    }

    #[test]
    fn game_music_bleed_does_not_trigger_mic_transmission() {
        let mut filter = MicFilter::new();
        // Simulate game music playing through `horizon_client_render::audio` at stereo 44.1 kHz.
        let music_stereo: Vec<f32> = (0..882 * 2)
            .map(|i| ((i as f32 * 0.07).sin()) * 0.10)
            .collect();
        push_game_audio_reference(&music_stereo, 2, 44_100);

        // Music bleeds into the phone microphone at amplitude 0.04 (-28 dBFS, well above -50 dB threshold).
        let mic_music_bleed: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.07).sin()) * 0.04)
            .collect();
        let out = filter.process_frame(&mic_music_bleed, &[], 0.0, 1.0, -50.0, true);
        assert!(
            out.ducked,
            "expected game music bleed to be ducked when game audio is playing"
        );
        assert!(
            !out.speaking,
            "expected game music bleed not to trigger microphone transmission"
        );

        // Reset game audio reference to silence for subsequent tests.
        for _ in 0..ENVELOPE_TAPS {
            push_game_audio_reference(&[0.0; 1764], 2, 44_100);
        }
    }

    #[test]
    fn long_session_echo_canceler_remains_stable_after_five_minutes() {
        let mut aec = EnvelopeEchoCanceler::default();
        let hist = [0.05f32; ENVELOPE_TAPS];
        // Simulate 15,000 frames (5 minutes at 20 ms/frame) of continuous speaker + mic bleed.
        let mut last_echo = 0.0f32;
        let mut last_gain = 1.0f32;
        for i in 0..15_000 {
            let mic_rms = 0.04 + ((i % 17) as f32) * 0.002;
            let ref_rms = 0.08 + ((i % 13) as f32) * 0.003;
            let (echo_est, gain) = aec.estimate_and_suppress(mic_rms, ref_rms, &hist, 1.5);
            assert!(echo_est.is_finite() && echo_est > 0.01 && echo_est < 1.0);
            assert!(gain.is_finite() && (0.04..=1.0).contains(&gain));
            last_echo = echo_est;
            last_gain = gain;
        }
        assert!(
            last_echo > 0.02 && last_gain < 0.30,
            "after 5 minutes of operation, echo canceler must remain bounded and active (echo={last_echo}, gain={last_gain})"
        );
    }

    #[test]
    fn rnnoise_processes_normal_speech_without_clipping() {
        let mut filter = MicFilter::new();
        let speech_like: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.05).sin()) * 0.25)
            .collect();
        let out = filter.process_frame(&speech_like, &[], 0.0, 2.0, -45.0, true);
        assert_eq!(out.samples.len(), FRAME);
        assert!(
            out.samples.iter().all(|&s| s.is_finite() && s.abs() <= 1.0),
            "all output samples must remain finite and within [-1.0, 1.0] soft-limiter bounds"
        );
    }
}
