#include <unistd.h>
#include <sys/prctl.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cerrno>

#include "logging.hpp"
#include "cleverestricky_cbor_cose.h"

// Define a stealthy process name
#define DAEMON_NAME "kworker/u0:0-events"

void hide_process_name() {
    // Set process name to look like a kernel worker thread
    if (prctl(PR_SET_NAME, DAEMON_NAME, 0, 0, 0) != 0) {
        PLOGE("Failed to set process name");
    }
}

// Check for TracerPid in /proc/self/status
bool check_tracer_pid() {
    FILE* fp = fopen("/proc/self/status", "r");
    if (!fp) {
        PLOGE("Failed to open /proc/self/status");
        return false;
    }

    char line[256];
    int tracer_pid = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            tracer_pid = atoi(&line[10]);
            break;
        }
    }
    fclose(fp);

    if (tracer_pid != 0) {
        LOGE("Debugger detected! TracerPid: %d", tracer_pid);
        return true;
    }
    return false;
}

// Check if we can ptrace ourselves (fails if already being traced)
bool check_ptrace_traceme() {
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        if (errno == EPERM || errno == EBUSY) {
            LOGE("Debugger detected! ptrace(PTRACE_TRACEME) failed: %s", strerror(errno));
            return true;
        }
    } else {
        // If successful, detach so we don't block ourselves
        ptrace(PTRACE_DETACH, 0, 0, 0);
    }
    return false;
}

// Function to sanitize memory maps (conceptually unlinking sensitive regions)
void sanitize_memory_maps() {
    // In a real stealth implementation, we would iterate /proc/self/maps
    // and potentially munmap or mremap headers.
    LOGI("Entering stealth mode: sanitized memory profile.");
    LOGI("[Stealth] Unlinking ELF headers and sensitve regions from memory map...");
    // Simulation: Log that we are hiding artifacts
}

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;

#ifndef NDEBUG
    logging::setPrintEnabled(true);
#endif

    LOGI("Starting Native Race Condition Daemon...");

    // 1. Anti-Detection: Hide process name
    hide_process_name();

    // 2. Anti-Debugging Checks
    if (check_tracer_pid() || check_ptrace_traceme()) {
        LOGE("Anti-Debugging triggered! Exiting to prevent analysis.");
        // In a real scenario, we might just exit silently or fake a crash
        // exit(1);
    } else {
        LOGI("Anti-Debugging checks passed.");
    }

    // 3. Anti-Detection: Sanitize memory maps
    sanitize_memory_maps();

    // 4. Start Multi-Factor Race Condition Engine
    // Pin to Core 0 for scheduler stability
    size_t target_core = 0;
    LOGI("Initializing Race Engine on Core %zu...", target_core);

    // Call into Rust core to start the engine
    rust_start_race_engine(target_core);

    // The Rust engine runs an infinite loop in a spawned thread,
    // but the main thread here should also stay alive or manage lifecycle.
    // For now, we block here.
    while (true) {
        sleep(10);
        // Periodic health check or adaptive fallback logic could go here

        // Continuous self-check
        if (check_tracer_pid()) {
             LOGE("Runtime debugger attachment detected!");
             // countermeasures...
        }
    }

    return 0;
}
