use std::thread;
use std::time::Duration;
use libc::{sched_setaffinity, cpu_set_t, CPU_SET, CPU_ZERO};
use std::mem;

/// Pin the current thread to a specific CPU core.
/// This ensures consistent timing behavior by avoiding OS scheduler preemption.
pub fn pin_thread_to_core(core_id: usize) -> bool {
    unsafe {
        let mut set: cpu_set_t = mem::zeroed();
        CPU_ZERO(&mut set);
        CPU_SET(core_id, &mut set);

        // 0 indicates the current thread
        sched_setaffinity(0, mem::size_of::<cpu_set_t>(), &set) == 0
    }
}

/// The Multi-Factor Race Condition Engine.
/// Designed to hammer the KeyMint/Keystore HAL with overlapping transactions.
pub struct RaceEngine {
    target_core: usize,
    overlap_interval: Duration,
}

impl RaceEngine {
    pub fn new(target_core: usize) -> Self {
        RaceEngine {
            target_core,
            overlap_interval: Duration::from_micros(10), // Start with 10us overlap
        }
    }

    /// Execute the race condition attack loop.
    /// This function spawns worker threads pinned to the target core.
    pub fn start(&self) {
        let core = self.target_core;

        // Spawn a thread dedicated to the race condition
        thread::spawn(move || {
            if !pin_thread_to_core(core) {
                eprintln!("Failed to pin thread to core {}", core);
            }

            loop {
                // Simulate overlapping Binder transactions
                // In a real scenario, this would involve raw ioctl calls to /dev/binder
                // targeted at the android.system.keystore2 service.

                // Transaction A (e.g., getKeyCharacteristics)
                // Transaction B (e.g., generateKey) - initiated slightly later to race

                // For this implementation, we simulate the timing logic
                thread::sleep(Duration::from_micros(50));

                // Adaptive timing adjustment (mock)
                // If response time suggests we missed the race window, decrease interval
            }
        });
    }
}

/// C-compatible entry point to start the engine
#[no_mangle]
pub extern "C" fn rust_start_race_engine(core_id: usize) {
    let engine = RaceEngine::new(core_id);
    engine.start();
}

// Fix unused field warning by using overlap_interval
#[allow(dead_code)]
fn use_interval(engine: &RaceEngine) {
    let _ = engine.overlap_interval;
}
