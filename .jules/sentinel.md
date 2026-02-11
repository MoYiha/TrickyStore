## 2026-01-28 - [Data Leak in XML Parsing Exceptions]
**Vulnerability:** The application was logging the full `Throwable` object when XML parsing failed. In `XmlPullParserException`, the exception message often contains a snippet of the XML content where the error occurred. Since `keybox.xml` contains private keys, this could leak sensitive data to the logs.
**Learning:** Standard exception logging (`Logger.e(msg, t)`) is dangerous when processing sensitive data files with parsers that include content in error messages.
**Prevention:** Catch exceptions during sensitive data parsing and log generic error messages or sanitized exception types (e.g., `t.getClass().getSimpleName()`) instead of the full exception object.

## 2024-05-23 - [CRITICAL] Unsecured Sensitive Configuration Storage
**Vulnerability:** The configuration directory `/data/adb/tricky_store/` and sensitive files like `keybox.xml` (containing private keys) were created with default permissions (likely 755/644), making them readable by any app on the device.
**Learning:** Default filesystem operations (`mkdir`, `cp`) in Android/Linux usually respect umask (022 for root), resulting in world-readable files. Sensitive data must always have explicit permission hardening.
**Prevention:** Always use `chmod 700` for directories and `chmod 600` for files containing secrets immediately after creation. Enforce this in both installation scripts (`customize.sh`) and runtime initialization (Java/Kotlin using `Os.chmod`).

## 2024-05-24 - [Unintended Network Exposure of Local Service]
**Vulnerability:** The internal configuration web server (`WebServer`) was initialized using `NanoHTTPD(port)`, which defaults to binding on all network interfaces (`0.0.0.0`). This exposed the sensitive configuration API and token auth to the local network (e.g., Wi-Fi).
**Learning:** Embedded web servers often default to promiscuous binding. For local IPC or configuration tools, explicit binding to loopback (`127.0.0.1`) is mandatory.
**Prevention:** Always explicitly specify the hostname/IP when initializing network listeners for local services (e.g., `NanoHTTPD("127.0.0.1", port)`).

## 2024-05-23 - Race Condition in Token File Creation
**Vulnerability:** The `web_port` file containing the authentication token was being created in the configuration directory before the directory's permissions were restricted to `0700`. This created a window where the directory and the token file could be world-readable.
**Learning:** Even if you change file permissions immediately after creation, there is a race condition window. Securing the parent directory *before* creating sensitive files inside it is a more robust way to prevent access.
**Prevention:** Always ensure the parent directory has restrictive permissions (e.g., `0700`) before writing sensitive files into it. Use `Os.chmod` immediately after directory creation.

## 2024-05-29 - [Configuration Injection in Space-Delimited Files]
**Vulnerability:** The application stored configuration (`app_config`) as a space-delimited text file but accepted unvalidated user input for fields. This allowed "Configuration Injection" where attackers could insert newlines or spaces to create fake entries or corrupt the file structure.
**Learning:** Simple text-based file formats are prone to injection if delimiters (spaces, newlines) are not strictly forbidden in the input data.
**Prevention:** Always validate that user input does not contain the delimiters used by the storage format. For space-delimited files, reject any input matching `\s`.

## 2025-05-30 - [Inconsistent Permission Initialization]
**Vulnerability:** The service initialization logic (`Main.kt`) explicitly set the configuration directory permissions to `0755` (readable by all), overriding the secure `0700` permissions set during installation. This created a vulnerability window on every boot where sensitive files could potentially be read by other applications if their individual file permissions were weak.
**Learning:** Security configurations distributed across multiple initialization points (installer scripts vs. runtime code) can drift and contradict each other. Runtime initialization code should treat the secure state as the source of truth, not defaults.
**Prevention:** Centralize security configuration constants and verify that all initialization paths (install, boot, runtime) enforce the same strict permissions (`0700` for config dirs).

## 2026-05-30 - [Inconsistent SecureFile Usage (TOCTOU)]
**Vulnerability:** Found Config.kt and Main.kt using standard File.writeText followed by setReadable or relying on directory permissions, creating TOCTOU race conditions and potential for default permissions.
**Learning:** Security utilities like SecureFile are only effective if used consistently. Manually setting permissions after creation is always vulnerable to race conditions.
**Prevention:** Enforce usage of SecureFile.writeText for all sensitive file writes via lint rules or code review checklists. Never use standard File write methods for config files.

## 2026-06-05 - [Environment Injection in Sourced Configuration Files]
**Vulnerability:** The `spoof_build_vars` configuration file, intended for key-value storage, allowed keys that correspond to dangerous environment variables (e.g., `LD_PRELOAD`, `PATH`) and permitted backslashes (`\`) for line continuation. If this file were ever sourced by a shell script (a common pattern for config files), it could lead to arbitrary code execution or privilege escalation.
**Learning:** Configuration files that follow shell syntax (`KEY=VALUE`) are inherently risky if they are ever processed by a shell. Even if intended only for parsing, defensive programming requires assuming the worst-case usage (sourcing).
**Prevention:** Strictly validate keys against a whitelist or blacklist (e.g., block `LD_.*`, `PATH`). Disallow shell metacharacters like `\` and `()` that enable command chaining or subshells, even if the primary consumer is not a shell.

## 2026-06-08 - [Partial CRL Parsing Vulnerability (Fail Open)]
**Vulnerability:** `KeyboxVerifier` swallowed exceptions during streaming JSON parsing of the Certificate Revocation List (CRL). If the connection dropped or the JSON was truncated after valid entries, the verifier would return a partial list of revoked keys, treating the missing ones as valid.
**Learning:** Streaming parsers (like `JsonReader`) must explicitly handle and propagate errors. Catching `Exception` broadly without re-throwing in a security-critical verification loop leads to "Fail Open" behavior.
**Prevention:** Always ensure that verification logic defaults to "Fail Closed". If a revocation list cannot be fully parsed, the entire verification process must fail or return an error state, rather than a partial success.

## 2026-02-11 - [DoS Vulnerability in WebServer: Missing Rate Limiting & IPv6 Handling]
**Vulnerability:** The `WebServer` lacked rate limiting, allowing a local attacker (or malicious app on device) to flood the service and cause Denial of Service. Additionally, implementing rate limiting revealed a pitfall in IPv6 handling: stripping the port from `session.remoteIpAddress` (e.g. `::1`) caused IPv6 addresses to be truncated to empty strings, leading to all IPv6 clients sharing a single rate bucket.
**Learning:**
1. Embedded web servers on `0.0.0.0` are accessible to the local network and must be rate-limited.
2. Naive IP parsing (assuming `:` implies port) is dangerous in an IPv6 world. `NanoHTTPD` provides clean IPs, so manual stripping is unnecessary and harmful.
3. Unbounded maps for rate limiting (storing every IP) lead to memory leaks (OOM DoS).
**Prevention:**
1. Implement Token Bucket or Fixed Window rate limiting on all public endpoints.
2. Use robust IP parsing libraries (like `InetAddress`) or trust the framework's normalized output; avoiding manual string manipulation on IPs.
3. Always bound the size of security caches (like rate limit maps) to prevent memory exhaustion attacks.
