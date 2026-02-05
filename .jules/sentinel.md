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
