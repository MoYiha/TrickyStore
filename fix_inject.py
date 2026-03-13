import re

with open("module/src/main/cpp/inject/main.cpp", "r") as f:
    content = f.read()

# Replace the part where it reads remote_msg_hdr_after_recv
old_code = """
            struct msghdr remote_msg_hdr_after_recv{};
            if (read_proc(pid, remote_msghdr_ptr, &remote_msg_hdr_after_recv, sizeof(remote_msg_hdr_after_recv)) == sizeof(remote_msg_hdr_after_recv)) {
                LOGD(
                    "recvmsg remote msghdr: controllen=%zu flags=0x%x iovlen=%zu",
                    remote_msg_hdr_after_recv.msg_controllen,
                    remote_msg_hdr_after_recv.msg_flags,
                    remote_msg_hdr_after_recv.msg_iovlen
                );
            } else {
                LOGW("Failed to read remote msghdr after recvmsg");
            }

            if (read_proc(pid, remote_cmsg_buffer_ptr, &cmsg_buffer, sizeof(cmsg_buffer)) != sizeof(cmsg_buffer)) {
                LOGE("Failed to read cmsg_buffer from remote process");
                close_remote_fd_func(remote_fd);
                ptrace(PTRACE_DETACH, pid, 0, 0);
                return false;
            }

            // Re-construct msghdr with the local cmsg_buffer to interpret the received FD.
            // Iterate through cmsg entries because SCM_RIGHTS may not be first
            // (kernel can prepend SCM_CREDENTIALS / SCM_SECURITY).
            struct msghdr received_hdr_validation{};
            received_hdr_validation.msg_control = cmsg_buffer; // Use the buffer read from remote
            received_hdr_validation.msg_controllen = sizeof(cmsg_buffer);

            int received_fd = -1;
            for (cmsghdr *received_cmsg = CMSG_FIRSTHDR(&received_hdr_validation);
                 received_cmsg != nullptr;
                 received_cmsg = CMSG_NXTHDR(&received_hdr_validation, received_cmsg)) {
"""

new_code = """
            struct msghdr remote_msg_hdr_after_recv{};
            if (read_proc(pid, remote_msghdr_ptr, &remote_msg_hdr_after_recv, sizeof(remote_msg_hdr_after_recv)) == sizeof(remote_msg_hdr_after_recv)) {
                LOGD(
                    "recvmsg remote msghdr: controllen=%zu flags=0x%x iovlen=%zu",
                    remote_msg_hdr_after_recv.msg_controllen,
                    remote_msg_hdr_after_recv.msg_flags,
                    remote_msg_hdr_after_recv.msg_iovlen
                );
            } else {
                LOGE("Failed to read remote msghdr after recvmsg");
                close_remote_fd_func(remote_fd);
                ptrace(PTRACE_DETACH, pid, 0, 0);
                return false;
            }

            if (read_proc(pid, remote_cmsg_buffer_ptr, &cmsg_buffer, sizeof(cmsg_buffer)) != sizeof(cmsg_buffer)) {
                LOGE("Failed to read cmsg_buffer from remote process");
                close_remote_fd_func(remote_fd);
                ptrace(PTRACE_DETACH, pid, 0, 0);
                return false;
            }

            // Re-construct msghdr with the local cmsg_buffer to interpret the received FD.
            // Iterate through cmsg entries because SCM_RIGHTS may not be first
            // (kernel can prepend SCM_CREDENTIALS / SCM_SECURITY).
            struct msghdr received_hdr_validation{};
            received_hdr_validation.msg_control = cmsg_buffer; // Use the buffer read from remote
            // Use the actual controllen returned by recvmsg so CMSG_NXTHDR doesn't read zeroed memory
            received_hdr_validation.msg_controllen = remote_msg_hdr_after_recv.msg_controllen;

            int received_fd = -1;
            for (cmsghdr *received_cmsg = CMSG_FIRSTHDR(&received_hdr_validation);
                 received_cmsg != nullptr;
                 received_cmsg = CMSG_NXTHDR(&received_hdr_validation, received_cmsg)) {
                if (received_cmsg->cmsg_len == 0) {
                    LOGW("Received cmsg_len is 0, breaking to prevent infinite loop");
                    break;
                }
"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open("module/src/main/cpp/inject/main.cpp", "w") as f:
        f.write(content)
    print("Successfully replaced.")
else:
    print("Could not find code block to replace!")
