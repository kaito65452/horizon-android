//! Complex Voice Chat for Horizon: voice chat compatible with servers running Simple Voice Chat
//! 2.6.x (protocol compatibility version 20). Written from the protocol's
//! packet layouts; no code from the mod. Opus is the bundled libopus 1.5.2.
pub mod proto;
mod opus;
mod mixer;
mod device;
mod client;
pub mod icons;

pub use client::{
    GroupInfo, Payload, Player, Settings, State, Status, create_group, join, join_group, leave, leave_group, maintain,
    microphones, payload, push_to_talk, set_player_volume, set_settings, set_testing, speakers, status,
    toggle_disabled, toggle_mute, update_listener,
};
pub use proto::GroupKind;
