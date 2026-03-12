import re

with open("module/src/main/cpp/inject/main.cpp", "r") as f:
    content = f.read()

# Replace the part where it prepares cmsg_buffer
old_code = """
            // Use a large control buffer so SCM_RIGHTS is delivered even when
            // the kernel prepends extra ancillary data (SCM_CREDENTIALS,
            // SCM_SECURITY) on Android 14+ kernels with SO_PASSCRED/SO_PASSSEC.
            constexpr size_t CMSG_BUF_SIZE = 256;
            char cmsg_buffer[CMSG_BUF_SIZE] = {0};
            uintptr_t remote_cmsg_buffer_ptr = push_memory(pid, regs, &cmsg_buffer, sizeof(cmsg_buffer));
"""

new_code = """
            // Use a large control buffer so SCM_RIGHTS is delivered even when
            // the kernel prepends extra ancillary data (SCM_CREDENTIALS,
            // SCM_SECURITY) on Android 14+ kernels with SO_PASSCRED/SO_PASSSEC.
            constexpr size_t CMSG_BUF_SIZE = 1024;
            char cmsg_buffer[CMSG_BUF_SIZE] = {0};
            uintptr_t remote_cmsg_buffer_ptr = push_memory(pid, regs, &cmsg_buffer, sizeof(cmsg_buffer));
"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open("module/src/main/cpp/inject/main.cpp", "w") as f:
        f.write(content)
    print("Successfully replaced cmsg buffer size.")
else:
    print("Could not find cmsg buffer size block to replace!")
