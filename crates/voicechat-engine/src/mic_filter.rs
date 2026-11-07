//! Microphone signal conditioning for mobile and desktop speakers:
//! 1. Smart half-duplex ducking gate when remote players are audible on the mixer.
//! 2. Lightweight 64-tap NLMS adaptive echo suppressor using the mixer's mono reference.
//! 3. Post-AEC RNNoise (`nnnoiseless`) neural denoising at 48 kHz without pre-gain clipping,
//!    with VAD probability gating, 500 ms hangover hold, and post-filter soft limiting.

use crate::opus::FRAME;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicU32, Ordering::Relaxed};
use std::sync::{Mutex, OnceLock};

/// 10 ms subframe size required by `nnnoiseless::DenoiseState` at 48 kHz.
pub const RNNOISE_SUBFRAME: usize = 480;
/// Number of 20 ms frames to hold voice activation open after speech stops (25 * 20 ms = 500 ms).
pub const HANGOVER_FRAMES: u32 = 25;
/// Extra VAD threshold required (in dB) while remote voices are playing from the speakers.
pub const DUCKING_THRESHOLD_BOOST_DB: f32 = 12.0;
/// Attenuation factor (-12 dB) applied to the microphone while remote voices dominate.
pub const DUCKING_ATTENUATION: f32 = 0.25;
/// Maximum user mic gain allowed when RNNoise is active, preventing noise-floor over-amplification.
pub const MAX_RNNOISE_GAIN: f32 = 2.0;
/// Taps in the lightweight time-domain NLMS echo suppressor.
const NLMS_TAPS: usize = 64;
/// Maximum mono reference samples buffered from the speaker mixer (100 ms at 48 kHz).
const MAX_REF_SAMPLES: usize = 4800;

struct SpeakerRefState {
    mono_ring: VecDeque<f32>,
    smoothed_rms: f32,
    hangover_samples: usize,
}

static SPEAKER_REF: OnceLock<Mutex<SpeakerRefState>> = OnceLock::new();
static REMOTE_RMS_BITS: AtomicU32 = AtomicU32::new(0);

fn speaker_ref() -> &'static Mutex<SpeakerRefState> {
    SPEAKER_REF.get_or_init(|| {
        Mutex::new(SpeakerRefState {
            mono_ring: VecDeque::with_capacity(MAX_REF_SAMPLES),
            smoothed_rms: 0.0,
            hangover_samples: 0,
        })
    })
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
    if block_rms > state.smoothed_rms {
        state.smoothed_rms = state.smoothed_rms * 0.4 + block_rms * 0.6;
    } else {
        state.smoothed_rms = state.smoothed_rms * 0.90 + block_rms * 0.10;
    }
    if block_rms > 0.003 {
        // Hold remote-talking state for ~120 ms (5760 samples at 48 kHz) to cover room reverb tail.
        state.hangover_samples = 5760;
    } else {
        state.hangover_samples = state.hangover_samples.saturating_sub(frames);
    }
    let effective_rms = if state.hangover_samples > 0 {
        state.smoothed_rms.max(0.004)
    } else {
        state.smoothed_rms
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

/// Lightweight 64-tap Normalized Least Mean Squares (NLMS) adaptive echo suppressor.
pub struct NlmsFilter {
    weights: [f32; NLMS_TAPS],
    history: [f32; NLMS_TAPS],
    hist_pos: usize,
}

impl Default for NlmsFilter {
    fn default() -> Self {
        Self {
            weights: [0.0; NLMS_TAPS],
            history: [0.0; NLMS_TAPS],
            hist_pos: 0,
        }
    }
}

impl NlmsFilter {
    pub fn reset(&mut self) {
        self.weights = [0.0; NLMS_TAPS];
        self.history = [0.0; NLMS_TAPS];
        self.hist_pos = 0;
    }

    /// Suppresses linear echo from `mic` using aligned `speaker_ref` samples.
    pub fn process(&mut self, mic: &mut [f32], speaker_ref: &[f32]) {
        const MU: f32 = 0.12;
        const EPS: f32 = 1e-4;

        let len = mic.len().min(speaker_ref.len());
        if len == 0 {
            return;
        }
        let ref_energy: f32 = speaker_ref[..len].iter().map(|&x| x * x).sum::<f32>() / len as f32;
        if ref_energy < 1e-6 {
            return;
        }
        let mic_energy: f32 = mic[..len].iter().map(|&x| x * x).sum::<f32>() / len as f32;
        // Double-talk detector: freeze adaptation when near-end speech dominates reference energy.
        let adapt = mic_energy <= ref_energy * 2.25;

        for i in 0..len {
            let x = speaker_ref[i];
            self.history[self.hist_pos] = x;

            let mut echo_est = 0.0f32;
            let mut norm = EPS;
            let mut idx = self.hist_pos;
            for w in &self.weights {
                let h = self.history[idx];
                echo_est += *w * h;
                norm += h * h;
                idx = if idx == 0 { NLMS_TAPS - 1 } else { idx - 1 };
            }

            let err = (mic[i] - echo_est).clamp(-1.0, 1.0);
            mic[i] = err;

            if adapt {
                let step = (MU * err) / norm;
                let mut idx = self.hist_pos;
                for w in &mut self.weights {
                    *w += step * self.history[idx];
                    if !w.is_finite() || w.abs() > 4.0 {
                        self.reset();
                        return;
                    }
                    idx = if idx == 0 { NLMS_TAPS - 1 } else { idx - 1 };
                }
            }

            self.hist_pos = (self.hist_pos + 1) % NLMS_TAPS;
        }
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
    nlms: NlmsFilter,
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
            nlms: NlmsFilter::default(),
            hangover_left: 0,
        }
    }

    /// Processes a 960-sample 48 kHz mono frame with:
    /// 1. Smart ducking when `remote_rms` indicates remote voices are playing.
    /// 2. Lightweight NLMS adaptive echo suppression against `speaker_ref`.
    /// 3. Post-AEC RNNoise (if `rnnoise_enabled`) without pre-gain clipping.
    /// 4. Post-filter user gain (capped at 2.0x when RNNoise is active) + `tanh` soft limiter.
    /// 5. Combined dBFS + RNNoise VAD probability check with 500 ms hangover hold.
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

        let raw_rms = (working.iter().map(|&v| (v as f64).powi(2)).sum::<f64>() / len as f64).sqrt() as f32;
        let remote_talking = remote_rms > 0.003;
        // Layer 1: Smart ducking gate — attenuate mic by -12 dB when remote voices are playing
        // unless local speech clearly breaks through above the speaker bleed.
        let breakout_rms = (remote_rms * 2.5).max(0.04);
        let ducked = remote_talking && raw_rms < breakout_rms;
        if ducked {
            for s in &mut working {
                *s *= DUCKING_ATTENUATION;
            }
        }

        // Layer 3: Lightweight NLMS software echo suppression when speaker reference has energy.
        if remote_talking && !speaker_ref.is_empty() {
            self.nlms.process(&mut working, speaker_ref);
        }

        // Layer 4: Post-AEC RNNoise without pre-gain clipping, followed by post-filter gain.
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

        let effective_threshold = if remote_talking {
            (threshold_db + DUCKING_THRESHOLD_BOOST_DB).min(-6.0)
        } else {
            threshold_db
        };
        let min_vad = if !rnnoise_enabled {
            0.0
        } else if remote_talking {
            0.70
        } else {
            0.45
        };

        if level_db > effective_threshold && vad_prob >= min_vad {
            self.hangover_left = HANGOVER_FRAMES;
        } else if self.hangover_left > 0 {
            self.hangover_left -= 1;
        }
        let speaking = self.hangover_left > 0;

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
        // Low-level mic bleed (RMS ~0.01) while remote speaker is loud (RMS 0.08).
        let bleed: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.1).sin()) * 0.014)
            .collect();
        let speaker_ref: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.1).sin()) * 0.11)
            .collect();

        let out = filter.process_frame(&bleed, &speaker_ref, 0.08, 1.0, -45.0, false);
        assert!(out.ducked, "expected ducking gate to engage on speaker bleed");
        assert!(
            !out.speaking,
            "expected raised ducking threshold (+12 dB) to prevent false VAD trigger"
        );
        let out_rms =
            (out.samples.iter().map(|&x| x * x).sum::<f32>() / FRAME as f32).sqrt();
        let in_rms = (bleed.iter().map(|&x| x * x).sum::<f32>() / FRAME as f32).sqrt();
        assert!(
            out_rms < in_rms * 0.35,
            "expected ducked output RMS ({out_rms}) to be attenuated below input RMS ({in_rms})"
        );
    }

    #[test]
    fn rnnoise_caps_post_gain_and_holds_hangover() {
        let mut filter = MicFilter::new();
        let loud_voice: Vec<f32> = (0..FRAME)
            .map(|i| ((i as f32 * 0.065).sin() + (i as f32 * 0.13).cos()) * 0.25)
            .collect();
        let silence = vec![0.0f32; FRAME];

        // Even with user_gain = 4.0, post-filter soft limiter and 2.0x cap keep samples in (-1, 1).
        let out = filter.process_frame(&loud_voice, &[], 0.0, 4.0, -50.0, false);
        assert!(out.speaking);
        assert!(out.samples.iter().all(|&s| s.is_finite() && s.abs() < 1.0));

        // Subsequent silent frame stays `speaking = true` due to 500 ms hangover hold.
        let after = filter.process_frame(&silence, &[], 0.0, 1.0, -50.0, false);
        assert!(after.speaking, "expected 500 ms hangover hold to keep speaking active");
    }
}
