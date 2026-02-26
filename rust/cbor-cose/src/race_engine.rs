use libc::{cpu_set_t, sched_setaffinity, CPU_SET, CPU_ZERO};
use std::mem;
use std::sync::{
    atomic::{AtomicUsize, Ordering},
    Arc, Barrier,
};
use std::thread;
use std::time::Duration;

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
pub struct RaceManager {
    target_core: usize,
    shared_resource: Arc<AtomicUsize>,
    barrier: Arc<Barrier>,
}

impl RaceManager {
    pub fn new(target_core: usize) -> Self {
        RaceManager {
            target_core,
            shared_resource: Arc::new(AtomicUsize::new(0)),
            // Barrier for 2 threads (Attacker + Victim)
            barrier: Arc::new(Barrier::new(2)),
        }
    }

    /// Execute the race condition simulation.
    /// Spawns a Victim and an Attacker thread to simulate a TOCTOU vulnerability.
    pub fn start(&self) {
        let core = self.target_core;
        let resource = self.shared_resource.clone();
        let barrier = self.barrier.clone();

        // Spawn Victim Thread
        let resource_victim = resource.clone();
        let barrier_victim = barrier.clone();
        thread::spawn(move || {
            if !pin_thread_to_core(core) {
                eprintln!("Failed to pin victim thread to core {}", core);
            }

            loop {
                // Wait for attacker to be ready
                barrier_victim.wait();

                // 1. Check: Is resource free?
                if resource_victim.load(Ordering::Acquire) == 0 {
                    // Simulate processing time (Critical Window)
                    // This is where the TOCTOU happens
                    // In real Keystore, this might be checking permissions before key use
                    for _ in 0..100 {
                        std::hint::spin_loop();
                    }

                    // 2. Act: Claim resource
                    // If attacker modified it during the spin_loop, we have a race condition
                    // swap returns the PREVIOUS value
                    let prev_state = resource_victim.swap(1, Ordering::Release);

                    if prev_state != 0 {
                        // SUCCESS! We successfully raced the check.
                        // The resource was modified *after* our check but *before* our claim.
                        eprintln!(
                            "[RaceEngine] RACE CONDITION SUCCESS! Resource state was {} during claim (expected 0)",
                            prev_state
                        );
                    }
                }

                // Reset for next iteration (in a real attack we wouldn't reset, but this is a simulation loop)
                // We just ensure it's clean for the next barrier sync
                resource_victim.store(0, Ordering::Release);

                // Throttle slightly to not burn 100% CPU on loop overhead
                thread::sleep(Duration::from_micros(100));
            }
        });

        // Spawn Attacker Thread
        thread::spawn(move || {
            // Pin to a different core if possible, or same core to rely on scheduler slicing?
            // Usually race conditions are easier to trigger on different cores due to true parallelism.
            // Let's try pinning to core + 1
            let attack_core = if core + 1 < 8 { core + 1 } else { 0 };
            if !pin_thread_to_core(attack_core) {
                eprintln!("Failed to pin attacker thread to core {}", attack_core);
            }

            loop {
                // Synchronize start with victim
                barrier.wait();

                // Wait a tiny bit to hit the critical window in Victim
                // This delay needs to be tuned.
                // We want: Victim Checks (0) -> Attacker Modifies (2) -> Victim Claims (Overwrites 2 with 1).

                // Adjust this loop count to tune the race window
                for _ in 0..50 {
                    std::hint::spin_loop();
                }

                // Attempt to modify resource
                resource.store(2, Ordering::Release);

                // Wait for next cycle
                thread::sleep(Duration::from_micros(100));
            }
        });

        eprintln!(
            "[RaceEngine] Simulation started on Core {} (Victim) and {} (Attacker)",
            core,
            if core + 1 < 8 { core + 1 } else { 0 }
        );
    }
}

/// Internal entry point to start the engine, called by FFI wrapper
pub fn internal_start_race_engine(core_id: usize) {
    let engine = RaceManager::new(core_id);
    engine.start();
}
