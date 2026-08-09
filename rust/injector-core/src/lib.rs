//! Rust owned injector orchestration.
//!
//! The injector executable is entirely Rust. It owns argument parsing, logging,
//! symbol resolution, process tracing, architecture register operations,
//! ancillary parsing, resource cleanup, and every injection state transition.

#[cfg(target_os = "android")]
use std::panic::catch_unwind;

#[cfg(any(target_os = "android", test))]
mod abi;
#[cfg(target_os = "android")]
mod engine;
#[cfg(target_os = "android")]
mod logging;
#[cfg(target_os = "android")]
mod process_memory;
#[cfg(target_os = "android")]
mod ptrace_session;
#[cfg(target_os = "android")]
mod symbol_resolver;

#[cfg(target_os = "android")]
pub fn run_cli() -> i32 {
    catch_unwind(std::panic::AssertUnwindSafe(|| {
        let arguments: Vec<std::ffi::OsString> = std::env::args_os().collect();
        engine::run(&arguments)
    }))
    .unwrap_or(1)
}
