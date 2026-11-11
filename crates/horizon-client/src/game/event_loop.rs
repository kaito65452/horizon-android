//! What the game asks of the loop that runs it.
//!
//! A window's game runs inside winit's event loop, and its methods used to take winit's
//! `ActiveEventLoop` only to stop the loop or to say when to wake next. A headless client
//! (#4951) has no display, so it cannot have that loop: it runs its own, and hands the same
//! methods this trait instead. winit's loop implements it, so a window's game is unchanged.

use winit::event_loop::{ActiveEventLoop, ControlFlow};

pub(super) trait EventLoopCtl {
    /// Ends the loop after the current iteration (`ActiveEventLoop::exit`).
    fn exit(&self);
    /// When the loop wakes next (`ActiveEventLoop::set_control_flow`).
    fn set_control_flow(&self, flow: ControlFlow);
}

/// A loop that cannot be asked anything: a synthetic event (touch or keypad) that arrives
/// outside a loop step has no `ActiveEventLoop` to hand to `handle_window_event`.
#[cfg(target_os = "android")]
pub(super) struct Noop;

#[cfg(target_os = "android")]
impl EventLoopCtl for Noop {
    fn exit(&self) {
        horizon_client::android_runtime::return_to_launcher();
        crate::document_store::set_game_active(false);
        crate::diagnostics::finish();
        crate::platform::finish();
        std::thread::spawn(|| {
            std::thread::sleep(std::time::Duration::from_millis(150));
            std::process::exit(0);
        });
    }
    fn set_control_flow(&self, _flow: ControlFlow) {}
}

impl EventLoopCtl for ActiveEventLoop {
    fn exit(&self) {
        ActiveEventLoop::exit(self);
    }
    fn set_control_flow(&self, flow: ControlFlow) {
        ActiveEventLoop::set_control_flow(self, flow);
    }
}
