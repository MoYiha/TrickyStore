#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <android/log.h>
#include <sys/resource.h>

#define LOG_TAG "HALSupervisor"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// HAL WATCHDOG DAEMON
// Fork-Based Supervisor Daemon

void start_daemon() {
    // Daemon logic here
    ALOGI("Daemon started");
    while(1) {
        sleep(10);
    }
}

int main() {
    // Run at nice=10
    setpriority(PRIO_PROCESS, 0, 10);

    int backoff_ms = 500;
    const int max_backoff_ms = 30000; // 30s

    while (1) {
        pid_t pid = fork();

        if (pid < 0) {
            ALOGE("Fork failed");
            sleep(1);
            continue;
        }

        if (pid == 0) {
            // Child process
            start_daemon();
            exit(1);
        } else {
            // Parent supervisor
            int status;
            waitpid(pid, &status, 0);

            ALOGE("Daemon crashed or exited. Restarting with backoff %d ms", backoff_ms);

            struct timespec req;
            req.tv_sec = backoff_ms / 1000;
            req.tv_nsec = (backoff_ms % 1000) * 1000000L;
            nanosleep(&req, NULL);

            // Exponential backoff
            backoff_ms *= 2;
            if (backoff_ms > max_backoff_ms) {
                backoff_ms = max_backoff_ms;
            }
        }
    }

    return 0;
}
