#include "kernel_identity.hpp"

#include <sys/syscall.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <set>
#include <string>
#include <utility>

#include "lsplt.hpp"

namespace cleverestricky::kernel_identity {
namespace {
std::mutex g_config_mutex;
std::atomic<bool> g_enabled{false};
std::atomic<bool> g_hooks_installed{false};
std::string g_release;
std::string g_version;

bool valid_field(const std::string &value, size_t capacity) {
  if (value.empty() || value.size() >= capacity) return false;
  for (const unsigned char ch : value) {
    if (ch < 0x20 || ch > 0x7e || ch == '|') return false;
  }
  return true;
}

void copy_field(char *destination, size_t capacity, const std::string &value) {
  std::memset(destination, 0, capacity);
  std::memcpy(destination, value.data(), value.size());
}

int hooked_uname(struct utsname *buffer) {
  const int result = static_cast<int>(syscall(SYS_uname, buffer));
  if (result != 0 || buffer == nullptr || !g_enabled.load(std::memory_order_acquire)) return result;
  std::lock_guard<std::mutex> guard(g_config_mutex);
  if (!g_enabled.load(std::memory_order_relaxed)) return result;
  copy_field(buffer->release, sizeof(buffer->release), g_release);
  copy_field(buffer->version, sizeof(buffer->version), g_version);
  return result;
}

bool candidate_path(const std::string &path) {
  if (path.empty() || path[0] == '[' || path.find("libcleverestricky.so") != std::string::npos) return false;
  if (path.ends_with("/keystore2")) return true;
  return path.ends_with(".so") &&
         (path.starts_with("/system/") || path.starts_with("/apex/") || path.starts_with("/vendor/") ||
          path.starts_with("/product/") || path.starts_with("/system_ext/"));
}
}  // namespace

void configure(const char *payload) {
  bool enabled = false;
  std::string release;
  std::string version;
  if (payload != nullptr) {
    const std::string value(payload);
    const size_t first = value.find('|');
    const size_t second = first == std::string::npos ? std::string::npos : value.find('|', first + 1);
    if (first != std::string::npos && second != std::string::npos && value.substr(0, first) == "1") {
      release = value.substr(first + 1, second - first - 1);
      version = value.substr(second + 1);
      enabled = valid_field(release, sizeof(utsname{}.release)) && valid_field(version, sizeof(utsname{}.version));
    }
  }
  {
    std::lock_guard<std::mutex> guard(g_config_mutex);
    g_release = enabled ? release : std::string{};
    g_version = enabled ? version : std::string{};
    g_enabled.store(enabled, std::memory_order_release);
  }
}

bool install_hooks_if_enabled() {
  if (!g_enabled.load(std::memory_order_acquire)) return true;
  if (g_hooks_installed.load(std::memory_order_acquire)) return true;

  auto maps = lsplt::MapInfo::Scan();
  std::set<std::pair<dev_t, ino_t>> seen;
  size_t installed = 0;
  for (const auto &map : maps) {
    if (!candidate_path(map.path) || map.dev == 0 || map.inode == 0 || !seen.emplace(map.dev, map.inode).second) continue;
    void *backup = nullptr;
    if (!lsplt::RegisterHook(map.dev, map.inode, "uname", reinterpret_cast<void *>(hooked_uname), &backup)) continue;
    const bool committed = lsplt::CommitHook();
    if (committed && backup != nullptr) ++installed;
  }
  if (installed == 0) return false;
  g_hooks_installed.store(true, std::memory_order_release);
  return true;
}
}  // namespace cleverestricky::kernel_identity
