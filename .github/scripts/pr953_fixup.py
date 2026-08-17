#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess


def git_show(spec: str) -> str:
    return subprocess.check_output(["git", "show", spec], text=True)


def must_replace(text: str, old: str, new: str, label: str, count: int = 1) -> str:
    actual = text.count(old)
    if actual != count:
        raise SystemExit(f"{label}: expected {count} occurrence(s), found {actual}")
    return text.replace(old, new, count)


def replace_span(text: str, start: str, end: str, replacement: str, label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"{label}: start marker not found")
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:i] + replacement.rstrip() + "\n\n" + text[j:]


conflicts = set(
    subprocess.check_output(
        ["git", "diff", "--name-only", "--diff-filter=U"], text=True
    ).splitlines()
)
expected_conflicts = {"encryptor-app/build.gradle.kts", "module/template/customize.sh"}
if conflicts != expected_conflicts:
    raise SystemExit(f"unexpected merge conflicts: {sorted(conflicts)}")

build_path = Path("encryptor-app/build.gradle.kts")
build_ours = git_show(":2:encryptor-app/build.gradle.kts")
build_theirs = git_show(":3:encryptor-app/build.gradle.kts")
for name, value in (("ours", build_ours), ("master", build_theirs)):
    if "JavaVersion.VERSION_11" not in value or "jvmTarget = JvmTarget.JVM_11" not in value:
        raise SystemExit(f"encryptor-app semantic merge: {name} lost JVM 11")
if "cargo ndk" not in build_ours or "encryptor-native" not in build_ours:
    raise SystemExit("encryptor-app semantic merge: PR Rust/JNI integration missing")
build_path.write_text(build_ours)

customize_path = Path("module/template/customize.sh")
customize = git_show(":2:module/template/customize.sh")
customize_master = git_show(":3:module/template/customize.sh")
customize = must_replace(
    customize,
    "extract \"$ZIPFILE\" 'service.sh' \"$MODPATH\"\n",
    "extract \"$ZIPFILE\" 'service.sh' \"$MODPATH\"\nextract \"$ZIPFILE\" 'action.sh' \"$MODPATH\"\n",
    "customize action extraction",
)
customize = re.sub(r'^rm -f "\$MODPATH/action\.sh".*\n', '', customize, flags=re.M)
if "service.sh service.apk" in customize:
    customize = customize.replace("service.sh service.apk", "service.sh action.sh service.apk", 1)
elif "service.sh action.sh service.apk" not in customize:
    raise SystemExit("customize required payload list not found")
if '"$MODPATH/service.sh" "$MODPATH/post-fs-data.sh"' in customize:
    customize = customize.replace(
        '"$MODPATH/service.sh" "$MODPATH/post-fs-data.sh"',
        '"$MODPATH/service.sh" "$MODPATH/action.sh" "$MODPATH/post-fs-data.sh"',
        1,
    )
elif '"$MODPATH/action.sh"' not in customize:
    raise SystemExit("customize executable chmod list not found")
cleanup_marker = "normalize_conflicting_module_name() {"
if cleanup_marker not in customize_master:
    raise SystemExit("master installer conflict cleanup block missing")
cleanup_block = customize_master[customize_master.index(cleanup_marker):].strip()
if cleanup_marker not in customize:
    customize = customize.rstrip() + "\n\n" + cleanup_block + "\n"
customize_path.write_text(customize)

config_path = Path("service/src/main/java/cleveres/tricky/cleverestech/Config.kt")
config = config_path.read_text()
config = must_replace(
    config,
    '''    fun updateKeyBoxesSync() {\n        updateKeyBoxesSyncWith(\n            revocationProvider = { KeyboxVerifier.fetchCrl() },\n            verifier = { keybox, crl -> KeyboxVerifier.verifyKeybox(keybox, crl) },\n        )\n    }\n\n    fun updateKeyBoxesSync(revokedSerials: Set<String>?) {\n        updateKeyBoxesSyncWith(\n            revocationProvider = { revokedSerials },\n            verifier = { keybox, revoked -> KeyboxVerifier.verifyKeyboxLegacy(keybox, revoked) },\n        )\n    }''',
    '''    fun updateKeyBoxesSync(): Boolean =\n        updateKeyBoxesSyncWith(\n            revocationProvider = { KeyboxVerifier.fetchCrl() },\n            verifier = { keybox, crl -> KeyboxVerifier.verifyKeybox(keybox, crl) },\n        )\n\n    fun updateKeyBoxesSync(revokedSerials: Set<String>?): Boolean =\n        updateKeyBoxesSyncWith(\n            revocationProvider = { revokedSerials },\n            verifier = { keybox, revoked -> KeyboxVerifier.verifyKeyboxLegacy(keybox, revoked) },\n        )''',
    "Config keybox result propagation",
)
config = must_replace(
    config,
    '''    internal fun updateKeyBoxesSync(\n        revokedSerials: Set<String>?,\n        verifier: (CertHack.KeyBox, Set<String>) -> KeyboxVerifier.Status,\n    ) {\n        updateKeyBoxesSyncWith({ revokedSerials }, verifier)\n    }''',
    '''    internal fun updateKeyBoxesSync(\n        revokedSerials: Set<String>?,\n        verifier: (CertHack.KeyBox, Set<String>) -> KeyboxVerifier.Status,\n    ): Boolean = updateKeyBoxesSyncWith({ revokedSerials }, verifier)''',
    "Config test overload result propagation",
)
config_path.write_text(config)

web_path = Path("service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt")
web = web_path.read_text()
web = must_replace(
    web,
    '''    private fun updateKeyboxesFromConfiguredRevocationSource() {\n        val legacyFetcher = crlFetcher\n        if (legacyFetcher == null) {\n            Config.updateKeyBoxesSync()\n        } else {\n            Config.updateKeyBoxesSync(legacyFetcher())\n        }\n    }''',
    '''    private fun updateKeyboxesFromConfiguredRevocationSource(): Boolean =\n        crlFetcher?.let { Config.updateKeyBoxesSync(it()) } ?: Config.updateKeyBoxesSync()\n\n    private fun keyboxActivationFailureResponse(): Response =\n        secureResponse(\n            Response.Status.SERVICE_UNAVAILABLE,\n            "text/plain",\n            "Keybox activation unavailable; previous active snapshot preserved",\n        )''',
    "WebServer keybox activation helper",
)
pattern = re.compile(r"(?m)^(?P<indent>[ \t]+)updateKeyboxesFromConfiguredRevocationSource\(\)\s*$")
call_count = len(pattern.findall(web))
if call_count != 8:
    raise SystemExit(f"WebServer mutation call count changed: expected 8, found {call_count}")
web = pattern.sub(
    lambda m: (
        f"{m.group('indent')}if (!updateKeyboxesFromConfiguredRevocationSource()) {{\n"
        f"{m.group('indent')}    return keyboxActivationFailureResponse()\n"
        f"{m.group('indent')}}}"
    ),
    web,
)
web_path.write_text(web)

upload_test_path = Path("service/src/test/java/cleveres/tricky/cleverestech/WebServerUploadTest.kt")
upload_test = upload_test_path.read_text()
upload_test = upload_test.replace("import org.junit.Assert.assertThrows\n", "")
upload_test = must_replace(
    upload_test,
    '''    @Test\n    fun testValidUploadDoesNotReportSuccessWhenBackendActivationFails() {\n        KeyboxLoader.activeSetOverride = { false }\n        BackendRecovery.recoveryOverride = { false }\n\n        val responseCode = uploadKeybox("activation_failure.xml", TestKeyboxFixtures.validEcKeyboxXml)\n\n        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, responseCode)\n        assertThrows(IllegalStateException::class.java) {\n            cleveres.tricky.cleverestech.keystore.CertHack.getKeyboxCount()\n        }\n        assert(File(configDir, "keyboxes/activation_failure.xml").exists())\n    }''',
    '''    @Test\n    fun testValidUploadDoesNotReportSuccessWhenBackendActivationFails() {\n        KeyboxLoader.activeSetOverride = { true }\n        assertEquals(200, uploadKeybox("active.xml", TestKeyboxFixtures.validEcKeyboxXml))\n        val activeKeyboxCount = cleveres.tricky.cleverestech.keystore.CertHack.getKeyboxCount()\n\n        KeyboxLoader.activeSetOverride = { false }\n        BackendRecovery.recoveryOverride = { false }\n        val responseCode = uploadKeybox("activation_failure.xml", TestKeyboxFixtures.validEcKeyboxXml)\n\n        assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, responseCode)\n        assertEquals(activeKeyboxCount, cleveres.tricky.cleverestech.keystore.CertHack.getKeyboxCount())\n        assert(File(configDir, "keyboxes/activation_failure.xml").exists())\n    }''',
    "WebServer activation regression test",
)
upload_test_path.write_text(upload_test)

backend_instance_path = Path("rust/backend/src/backend_instance.rs")
backend_instance = backend_instance_path.read_text()
insert_marker = "pub fn handle(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {"
if "pub fn initialize_for_test" not in backend_instance:
    test_initializer = '''#[cfg(test)]\npub fn initialize_for_test(auth: [u8; BACKEND_AUTH_BYTES]) -> io::Result<()> {\n    if auth.iter().all(|byte| *byte == 0) {\n        return Err(io::Error::other("backend capability is invalid"));\n    }\n    if let Some(existing) = INSTANCE.get() {\n        return if matches(&existing.auth, &auth) {\n            Ok(())\n        } else {\n            Err(io::Error::other("backend instance already initialized"))\n        };\n    }\n    INSTANCE\n        .set(BackendInstance::generate(auth)?)\n        .map_err(|_| io::Error::other("backend instance already initialized"))\n}\n\n'''
    backend_instance = must_replace(
        backend_instance, insert_marker, test_initializer + insert_marker, "backend test initializer"
    )
backend_instance_path.write_text(backend_instance)

backend_main_path = Path("rust/backend/src/main.rs")
backend_main = backend_main_path.read_text()
backend_main = must_replace(
    backend_main,
    '''    fn backend_ping_uses_fixed_request_and_response_bounds() {\n        backend_instance::initialize().unwrap();\n        assert_eq!(\n            opcode_request_limit(backend_instance::OP_BACKEND_PING),\n            Some(backend_instance::REQUEST_BYTES)\n        );\n        assert_eq!(\n            opcode_response_limit(backend_instance::OP_BACKEND_PING),\n            Some(backend_instance::RESPONSE_BYTES)\n        );\n        let response = handle_request(\n            backend_instance::OP_BACKEND_PING,\n            vec![backend_instance::HANDSHAKE_VERSION],\n        )\n        .unwrap();\n        assert_eq!(response.len(), backend_instance::RESPONSE_BYTES);\n    }''',
    '''    fn backend_ping_uses_fixed_request_and_response_bounds() {\n        let auth = std::array::from_fn(|index| (index as u8).wrapping_add(1));\n        backend_instance::initialize_for_test(auth).unwrap();\n        assert_eq!(\n            opcode_request_limit(backend_instance::OP_BACKEND_PING),\n            Some(backend_instance::REQUEST_BYTES)\n        );\n        assert_eq!(\n            opcode_response_limit(backend_instance::OP_BACKEND_PING),\n            Some(backend_instance::RESPONSE_BYTES)\n        );\n        let mut request = Vec::with_capacity(backend_instance::REQUEST_BYTES);\n        request.push(backend_instance::HANDSHAKE_VERSION);\n        request.extend_from_slice(&auth);\n        let response = handle_request(backend_instance::OP_BACKEND_PING, request).unwrap();\n        assert_eq!(response.len(), backend_instance::RESPONSE_BYTES);\n    }''',
    "backend authenticated ping test",
)
backend_main_path.write_text(backend_main)

encryptor_path = Path("rust/encryptor-native/src/lib.rs")
encryptor = encryptor_path.read_text()
encryptor = replace_span(
    encryptor,
    "pub fn encrypt_cbox_v2(\n",
    "fn encrypt_cbox_v2_with_nonce(\n",
    '''fn random_material<const N: usize>() -> Result<Zeroizing<[u8; N]>, EncryptError> {\n    let mut bytes = std::mem::MaybeUninit::<[u8; N]>::uninit();\n    // SAFETY: the slice covers exactly the uninitialized array storage. We call assume_init only\n    // after getrandom reports that it filled the complete slice successfully.\n    let output = unsafe { std::slice::from_raw_parts_mut(bytes.as_mut_ptr().cast::<u8>(), N) };\n    getrandom::fill(output).map_err(|_| EncryptError::RandomUnavailable)?;\n    // SAFETY: successful getrandom::fill initialized every byte in the array above.\n    let initialized = unsafe { bytes.assume_init() };\n    Ok(Zeroizing::new(initialized))\n}\n\npub fn encrypt_cbox_v2(\n    author_bytes: &[u8],\n    xml_bytes: &[u8],\n    signature_base64: &[u8],\n    password: &str,\n) -> Result<Vec<u8>, EncryptError> {\n    let salt = random_material::<SALT_BYTES>()?;\n    let iv = random_material::<IV_BYTES>()?;\n    encrypt_cbox_v2_with_nonce(\n        author_bytes,\n        xml_bytes,\n        signature_base64,\n        password,\n        &salt,\n        &iv,\n    )\n}''',
    "encryptor random material",
)
encryptor = replace_span(
    encryptor,
    "    #[test]\n    fn deterministic_v2_output_round_trips_through_shared_crypto_core() {",
    "    #[test]\n    fn filenames_are_single_bounded_cbox_components() {",
    '''    fn valid_test_password() -> String {\n        std::iter::repeat_n('p', 16).collect()\n    }\n\n    fn short_test_password() -> String {\n        std::iter::repeat_n('p', 4).collect()\n    }\n\n    #[test]\n    fn v2_output_round_trips_through_shared_crypto_core() {\n        let salt = random_material::<SALT_BYTES>().unwrap();\n        let iv = random_material::<IV_BYTES>().unwrap();\n        let password = valid_test_password();\n        let output = encrypt_cbox_v2_with_nonce(\n            b"mobile-author",\n            VALID_XML,\n            b"ZHVtbXktc2lnbmF0dXJl",\n            &password,\n            &salt,\n            &iv,\n        )\n        .unwrap();\n        assert!(shared_header(&output));\n        let payload = decrypt_cbox(output, &password).unwrap();\n        assert_eq!(payload.author, "mobile-author");\n        assert_eq!(payload.xml_content.as_bytes(), VALID_XML);\n        assert_eq!(payload.signature_base64, "ZHVtbXktc2lnbmF0dXJl");\n        assert_eq!(payload.signature_version, 2);\n    }\n\n    #[test]\n    fn malformed_xml_and_passwords_fail_closed() {\n        let salt = random_material::<SALT_BYTES>().unwrap();\n        let iv = random_material::<IV_BYTES>().unwrap();\n        let password = valid_test_password();\n        let short_password = short_test_password();\n        assert_eq!(\n            encrypt_cbox_v2_with_nonce(b"", VALID_XML, b"", &password, &salt, &iv),\n            Err(EncryptError::InvalidInput)\n        );\n        assert_eq!(\n            encrypt_cbox_v2_with_nonce(b"author", VALID_XML, b"", &short_password, &salt, &iv),\n            Err(EncryptError::InvalidInput)\n        );\n        let dtd = br#"<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><AndroidAttestation><NumberOfKeyboxes>0</NumberOfKeyboxes></AndroidAttestation>"#;\n        assert_eq!(\n            encrypt_cbox_v2_with_nonce(b"author", dtd, b"", &password, &salt, &iv),\n            Err(EncryptError::InvalidInput)\n        );\n    }\n\n    #[test]\n    fn current_header_is_authenticated() {\n        let salt = random_material::<SALT_BYTES>().unwrap();\n        let iv = random_material::<IV_BYTES>().unwrap();\n        let password = valid_test_password();\n        let mut output =\n            encrypt_cbox_v2_with_nonce(b"author", VALID_XML, b"", &password, &salt, &iv).unwrap();\n        output[8] ^= 1;\n        assert!(decrypt_cbox(output, &password).is_err());\n    }\n\n    #[test]\n    fn stored_ciphertext_header_is_bounded_and_versioned() {\n        let salt = random_material::<SALT_BYTES>().unwrap();\n        let iv = random_material::<IV_BYTES>().unwrap();\n        let password = valid_test_password();\n        let output =\n            encrypt_cbox_v2_with_nonce(b"author", VALID_XML, b"", &password, &salt, &iv).unwrap();\n        assert!(has_supported_cbox_header(&output));\n        assert!(!has_supported_cbox_header(b"CBOX"));\n    }''',
    "encryptor test fixtures",
)
encryptor_path.write_text(encryptor)

daemon_path = Path("rust/daemon/src/main.rs")
daemon = daemon_path.read_text()
daemon = must_replace(
    daemon,
    "const BACKEND_CIRCUIT_COOLDOWN: Duration = Duration::from_secs(60);\n",
    "const BACKEND_CIRCUIT_COOLDOWN: Duration = Duration::from_secs(60);\nconst ADAPTER_STABLE_INTERVAL: Duration = Duration::from_secs(5 * 60);\nconst ADAPTER_MAX_BACKOFF: Duration = Duration::from_secs(30);\nconst ADAPTER_POLL_INTERVAL: Duration = Duration::from_millis(100);\n",
    "daemon adapter constants",
)
identity_code = '''#[derive(Clone, Copy, Debug, Eq, PartialEq)]\nstruct AdapterLease {\n    pid: u32,\n    generation: u32,\n}\n\n#[derive(Debug, Default)]\nstruct AdapterIdentity {\n    state: std::sync::atomic::AtomicU64,\n}\n\nimpl AdapterIdentity {\n    fn pack(lease: AdapterLease) -> u64 {\n        ((lease.generation as u64) << 32) | u64::from(lease.pid)\n    }\n\n    fn unpack(state: u64) -> Option<AdapterLease> {\n        let pid = state as u32;\n        (pid != 0).then_some(AdapterLease {\n            pid,\n            generation: (state >> 32) as u32,\n        })\n    }\n\n    fn current(&self) -> Option<AdapterLease> {\n        Self::unpack(self.state.load(std::sync::atomic::Ordering::Acquire))\n    }\n\n    fn publish(&self, pid: u32) -> AdapterLease {\n        assert_ne!(pid, 0);\n        loop {\n            let current = self.state.load(std::sync::atomic::Ordering::Acquire);\n            let lease = AdapterLease {\n                pid,\n                generation: ((current >> 32) as u32).wrapping_add(1),\n            };\n            if self\n                .state\n                .compare_exchange(\n                    current,\n                    Self::pack(lease),\n                    std::sync::atomic::Ordering::AcqRel,\n                    std::sync::atomic::Ordering::Acquire,\n                )\n                .is_ok()\n            {\n                return lease;\n            }\n        }\n    }\n\n    fn invalidate(&self, lease: AdapterLease) {\n        let invalid = (u64::from(lease.generation.wrapping_add(1))) << 32;\n        let _ = self.state.compare_exchange(\n            Self::pack(lease),\n            invalid,\n            std::sync::atomic::Ordering::AcqRel,\n            std::sync::atomic::Ordering::Acquire,\n        );\n    }\n\n    fn matches(&self, lease: AdapterLease) -> bool {\n        self.current() == Some(lease)\n    }\n}\n\n#[derive(Clone, Copy, Debug, Eq, PartialEq)]\nstruct AdapterRetryPlan {\n    rapid_failures: u32,\n    delay: Duration,\n}\n\n'''
daemon = must_replace(daemon, "fn main() {\n", identity_code + "fn main() {\n", "daemon adapter identity")
daemon = replace_span(
    daemon,
    "fn run() -> io::Result<()> {",
    "fn module_directory() -> io::Result<PathBuf> {",
    '''fn run() -> io::Result<()> {\n    harden_process()?;\n    let module_dir = module_directory()?;\n    validate_module_directory(&module_dir)?;\n\n    let config_root = Arc::new(config_file_broker::prepare_root()?);\n    let web_listener = bind_abstract(DAEMON_SOCKET_NAME)?;\n    let file_listener = bind_abstract(FILE_SOCKET_NAME)?;\n    let adapter_identity = Arc::new(AdapterIdentity::default());\n\n    let web_identity = Arc::clone(&adapter_identity);\n    thread::Builder::new()\n        .name("ct-web-ipc".to_string())\n        .spawn(move || {\n            if let Err(error) = serve_web(web_listener, web_identity) {\n                eprintln!("cleverestrickyd: WebUI IPC service failed: {error}");\n                process::exit(1);\n            }\n        })?;\n\n    spawn_capability_workers(\n        file_listener,\n        Arc::clone(&adapter_identity),\n        Arc::clone(&config_root),\n    )?;\n\n    let backend_dir = module_dir.clone();\n    let backend_root = Arc::clone(&config_root);\n    let backend_identity = Arc::clone(&adapter_identity);\n    thread::Builder::new()\n        .name("ct-backend".to_string())\n        .spawn(move || supervise_backend(backend_dir, backend_identity, backend_root))?;\n\n    let mut rapid_failures = 0u32;\n    loop {\n        let started = Instant::now();\n        match spawn_android_adapter(&module_dir) {\n            Ok(mut adapter) => {\n                let lease = adapter_identity.publish(adapter.id());\n                eprintln!(\n                    "cleverestrickyd: Android adapter generation {} started as pid {}",\n                    lease.generation, lease.pid\n                );\n                match adapter.wait() {\n                    Ok(status) => eprintln!(\n                        "cleverestrickyd: Android adapter generation {} exited with {status}",\n                        lease.generation\n                    ),\n                    Err(error) => eprintln!(\n                        "cleverestrickyd: Android adapter generation {} wait failed: {error}",\n                        lease.generation\n                    ),\n                }\n                adapter_identity.invalidate(lease);\n            }\n            Err(error) => eprintln!("cleverestrickyd: Android adapter launch failed: {error}"),\n        }\n\n        let plan = adapter_retry_plan(rapid_failures, started.elapsed());\n        rapid_failures = plan.rapid_failures;\n        eprintln!(\n            "cleverestrickyd: restarting Android adapter after {}s",\n            plan.delay.as_secs()\n        );\n        thread::sleep(plan.delay);\n    }\n}''',
    "daemon run supervisor",
)
daemon = replace_span(
    daemon,
    "fn run_backend_once(\n",
    "#[derive(Clone, Copy, Debug, Eq, PartialEq)]\nstruct BackendRetryPlan {",
    '''#[derive(Clone, Debug, Eq, PartialEq)]\nenum BackendRunOutcome {\n    Exited(String),\n    AdapterChanged,\n}\n\nfn run_backend_once(\n    module_dir: &Path,\n    lease: AdapterLease,\n    adapter_identity: &AdapterIdentity,\n    root: Arc<TrustedDir>,\n) -> io::Result<BackendRunOutcome> {\n    let (mut child, broker) = spawn_backend(module_dir, lease.pid)?;\n    let backend_pid = child.id();\n    let broker_thread = match thread::Builder::new()\n        .name("ct-keybox-broker".to_string())\n        .spawn(move || {\n            if let Err(error) = keybox_file_broker::serve(broker, &root) {\n                eprintln!("cleverestrickyd: keybox broker failed: {error}");\n                let _ = unsafe { libc::kill(backend_pid as libc::pid_t, libc::SIGTERM) };\n            }\n        }) {\n        Ok(handle) => handle,\n        Err(error) => {\n            let _ = child.kill();\n            let _ = child.wait();\n            return Err(error);\n        }\n    };\n\n    let outcome = loop {\n        if !adapter_identity.matches(lease) {\n            let _ = child.kill();\n            let _ = child.wait();\n            break BackendRunOutcome::AdapterChanged;\n        }\n        if let Some(status) = child.try_wait()? {\n            break BackendRunOutcome::Exited(format!("backend exited with {status}"));\n        }\n        thread::sleep(ADAPTER_POLL_INTERVAL);\n    };\n    broker_thread\n        .join()\n        .map_err(|_| io::Error::other("keybox broker thread panicked"))?;\n    Ok(outcome)\n}''',
    "daemon backend generation binding",
)
adapter_retry = '''fn adapter_retry_plan(previous_rapid_failures: u32, runtime: Duration) -> AdapterRetryPlan {\n    if runtime >= ADAPTER_STABLE_INTERVAL {\n        return AdapterRetryPlan {\n            rapid_failures: 0,\n            delay: Duration::from_secs(1),\n        };\n    }\n    let rapid_failures = previous_rapid_failures.saturating_add(1);\n    let backoff_seconds = 1u64 << rapid_failures.min(5);\n    AdapterRetryPlan {\n        rapid_failures,\n        delay: Duration::from_secs(backoff_seconds).min(ADAPTER_MAX_BACKOFF),\n    }\n}\n\n'''
daemon = must_replace(daemon, "fn supervise_backend(", adapter_retry + "fn supervise_backend(", "daemon adapter backoff")
daemon = replace_span(
    daemon,
    "fn supervise_backend(",
    "fn spawn_capability_workers(\n",
    '''fn supervise_backend(\n    module_dir: PathBuf,\n    adapter_identity: Arc<AdapterIdentity>,\n    root: Arc<TrustedDir>,\n) {\n    let mut rapid_failures = 0u32;\n    loop {\n        let Some(lease) = adapter_identity.current() else {\n            thread::sleep(ADAPTER_POLL_INTERVAL);\n            continue;\n        };\n        let started = Instant::now();\n        let outcome = run_backend_once(\n            &module_dir,\n            lease,\n            &adapter_identity,\n            Arc::clone(&root),\n        );\n        match outcome {\n            Ok(BackendRunOutcome::AdapterChanged) => {\n                rapid_failures = 0;\n                continue;\n            }\n            Ok(BackendRunOutcome::Exited(message)) => eprintln!("cleverestrickyd: {message}"),\n            Err(error) => eprintln!("cleverestrickyd: backend launch/wait failed: {error}"),\n        }\n\n        let plan = backend_retry_plan(rapid_failures, started.elapsed());\n        rapid_failures = plan.rapid_failures;\n        if plan.circuit_open {\n            eprintln!(\n                "cleverestrickyd: backend circuit open after {BACKEND_CIRCUIT_FAILURES} rapid failures; retrying after {}s",\n                plan.delay.as_secs()\n            );\n        }\n        thread::sleep(plan.delay);\n    }\n}''',
    "daemon backend supervisor",
)
daemon = replace_span(
    daemon,
    "fn spawn_capability_workers(\n",
    "fn handle_capability_request(\n",
    '''fn spawn_capability_workers(\n    listener: UnixListener,\n    adapter_identity: Arc<AdapterIdentity>,\n    root: Arc<TrustedDir>,\n) -> io::Result<()> {\n    for index in 0..CAPABILITY_WORKERS {\n        let worker_listener = listener.try_clone()?;\n        let worker_root = Arc::clone(&root);\n        let worker_identity = Arc::clone(&adapter_identity);\n        thread::Builder::new()\n            .name(format!("ct-file-ipc-{index}"))\n            .spawn(move || {\n                if let Err(error) =\n                    serve_capability_worker(worker_listener, worker_identity, worker_root)\n                {\n                    eprintln!("cleverestrickyd: file IPC worker failed: {error}");\n                    process::exit(1);\n                }\n            })?;\n    }\n    Ok(())\n}\n\nfn serve_capability_worker(\n    listener: UnixListener,\n    adapter_identity: Arc<AdapterIdentity>,\n    root: Arc<TrustedDir>,\n) -> io::Result<()> {\n    let mut scratch = vec![0u8; STREAM_COPY_BYTES];\n    loop {\n        let (mut client, _) = match listener.accept() {\n            Ok(value) => value,\n            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,\n            Err(error) => return Err(error),\n        };\n        let credentials = match peer_credentials(&client) {\n            Ok(value) if value.uid == 0 => value,\n            Ok(_) => continue,\n            Err(_) => continue,\n        };\n        client.set_read_timeout(Some(CLIENT_TIMEOUT))?;\n        client.set_write_timeout(Some(CLIENT_TIMEOUT))?;\n        let peer_pid = u32::try_from(credentials.pid).ok();\n        let peer_is_adapter = adapter_identity\n            .current()\n            .is_some_and(|lease| peer_pid == Some(lease.pid));\n        if let Err(error) =\n            handle_capability_request(&mut client, peer_is_adapter, &root, &mut scratch)\n        {\n            if !matches!(\n                error.kind(),\n                io::ErrorKind::UnexpectedEof\n                    | io::ErrorKind::ConnectionReset\n                    | io::ErrorKind::BrokenPipe\n                    | io::ErrorKind::TimedOut\n                    | io::ErrorKind::WouldBlock\n            ) {\n                eprintln!("cleverestrickyd: capability request transport failed: {error}");\n            }\n        }\n    }\n}''',
    "daemon capability generation checks",
)
daemon = replace_span(
    daemon,
    "fn serve_web(",
    "fn forward_web_request_with_timeout(\n",
    '''struct RegisteredAdapter {\n    stream: UnixStream,\n    lease: AdapterLease,\n}\n\nfn serve_web(listener: UnixListener, adapter_identity: Arc<AdapterIdentity>) -> io::Result<()> {\n    let mut adapter: Option<RegisteredAdapter> = None;\n    let mut relay_buffer = vec![0u8; STREAM_COPY_BYTES];\n    loop {\n        let (mut client, _) = match listener.accept() {\n            Ok(value) => value,\n            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,\n            Err(error) => return Err(error),\n        };\n        let credentials = match peer_credentials(&client) {\n            Ok(value) if value.uid == 0 => value,\n            Ok(_) => continue,\n            Err(_) => continue,\n        };\n        client.set_read_timeout(Some(CLIENT_TIMEOUT))?;\n        client.set_write_timeout(Some(CLIENT_TIMEOUT))?;\n        let header = match read_header_bounded(&mut client, MAX_FRAME_BYTES) {\n            Ok(value) => value,\n            Err(error) => {\n                let _ = reply_error(&mut client, OP_PING, &error);\n                continue;\n            }\n        };\n        if adapter\n            .as_ref()\n            .is_some_and(|registered| !adapter_identity.matches(registered.lease))\n        {\n            adapter = None;\n        }\n        let peer_pid = u32::try_from(credentials.pid).ok();\n        let peer_lease = adapter_identity\n            .current()\n            .filter(|lease| peer_pid == Some(lease.pid));\n\n        match header.opcode {\n            OP_ADAPTER_REGISTER => {\n                let Some(lease) = peer_lease else {\n                    let _ = reply_text_error(\n                        &mut client,\n                        OP_ADAPTER_REGISTER,\n                        "invalid adapter registration",\n                    );\n                    continue;\n                };\n                if header.flags != 0 || header.payload_len != 0 {\n                    let _ = reply_text_error(\n                        &mut client,\n                        OP_ADAPTER_REGISTER,\n                        "invalid adapter registration",\n                    );\n                    continue;\n                }\n                write_frame(&mut client, OP_ADAPTER_REGISTER, 0, b"ok")?;\n                adapter = Some(RegisteredAdapter { stream: client, lease });\n            }\n            OP_PING if header.flags == 0 && header.payload_len == 0 => {\n                write_frame(&mut client, OP_PING, 0, b"pong")?;\n            }\n            OP_WEB_REQUEST if header.flags == 0 && header.payload_len <= MAX_FRAME_BYTES => {\n                if let Err(error) = forward_web_request_with_timeout(\n                    &mut client,\n                    header,\n                    &mut adapter,\n                    &mut relay_buffer,\n                    CLIENT_TIMEOUT,\n                ) {\n                    adapter = None;\n                    let _ = reply_error(&mut client, OP_WEB_REQUEST, &error);\n                }\n            }\n            _ => {\n                let _ = reply_text_error(&mut client, header.opcode, "unsupported IPC operation");\n            }\n        }\n    }\n}''',
    "daemon WebUI adapter generations",
)
daemon = replace_span(
    daemon,
    "fn forward_web_request_with_timeout(\n",
    "fn reply_error(",
    '''fn forward_web_request_with_timeout(\n    client: &mut UnixStream,\n    request: FrameHeader,\n    adapter: &mut Option<RegisteredAdapter>,\n    scratch: &mut [u8],\n    timeout: Duration,\n) -> io::Result<()> {\n    let target = &mut adapter\n        .as_mut()\n        .ok_or_else(|| {\n            io::Error::new(\n                io::ErrorKind::NotConnected,\n                "Android adapter is unavailable",\n            )\n        })?\n        .stream;\n    target.set_read_timeout(Some(timeout))?;\n    target.set_write_timeout(Some(timeout))?;\n    write_header(target, request)?;\n    relay_exact(client, target, request.payload_len, scratch)?;\n\n    let response = read_header_bounded(target, MAX_FRAME_BYTES)?;\n    if response.opcode != OP_WEB_REQUEST || response.flags != 0 {\n        return Err(io::Error::new(\n            io::ErrorKind::InvalidData,\n            "adapter returned an invalid response header",\n        ));\n    }\n    write_header(client, response)?;\n    relay_exact(target, client, response.payload_len, scratch)\n}''',
    "daemon WebUI forwarding",
)
if "fn adapter_identity_rejects_stale_generation()" not in daemon:
    insert_at = daemon.rfind("\n}")
    if insert_at < 0:
        raise SystemExit("daemon test module closing brace not found")
    lifecycle_tests = '''\n\n    #[test]\n    fn adapter_identity_rejects_stale_generation() {\n        let identity = AdapterIdentity::default();\n        let first = identity.publish(101);\n        assert!(identity.matches(first));\n        identity.invalidate(first);\n        assert!(!identity.matches(first));\n        let second = identity.publish(101);\n        assert_ne!(first.generation, second.generation);\n        assert!(!identity.matches(first));\n        assert!(identity.matches(second));\n    }\n\n    #[test]\n    fn adapter_restart_backoff_is_bounded_and_resets_after_stability() {\n        let mut failures = 0;\n        for _ in 0..16 {\n            let plan = adapter_retry_plan(failures, Duration::from_secs(1));\n            assert!(plan.delay <= ADAPTER_MAX_BACKOFF);\n            failures = plan.rapid_failures;\n        }\n        let stable = adapter_retry_plan(failures, ADAPTER_STABLE_INTERVAL);\n        assert_eq!(stable.rapid_failures, 0);\n        assert_eq!(stable.delay, Duration::from_secs(1));\n    }'''
    daemon = daemon[:insert_at] + lifecycle_tests + daemon[insert_at:]
daemon_path.write_text(daemon)

perf_path = Path("docs/Performance.md")
perf = perf_path.read_text()
perf, count = re.subn(
    r"## Measured migration artifacts[\s\S]*?(?=## Physical-device)",
    "## Reproducible artifact measurements\n\n"
    "Artifact sizes and hashes are intentionally not pinned to an intermediate PR head. For a "
    "release candidate, use the artifact from the Build run attached to the exact commit being "
    "reviewed and record the run ID, artifact ID, archive SHA-256, and per-binary sizes together.\n\n",
    perf,
    count=1,
)
if count != 1:
    raise SystemExit("Performance.md stale artifact section not found")
perf_path.write_text(perf)

config = config_path.read_text()
web = web_path.read_text()
assert re.search(r"fun\s+updateKeyBoxesSync\(\)\s*:\s*Boolean\s*=\s*updateKeyBoxesSyncWith", config)
assert re.search(r"fun\s+updateKeyBoxesSync\(revokedSerials:\s*Set<String>\?\)\s*:\s*Boolean\s*=\s*updateKeyBoxesSyncWith", config)
assert re.search(r"fun\s+updateKeyboxesFromConfiguredRevocationSource\(\)\s*:\s*Boolean\s*=", web)
assert "Status.SERVICE_UNAVAILABLE" in web
assert re.search(r"if\s*\(!updateKeyboxesFromConfiguredRevocationSource\(\)\)\s*\{?\s*return\s+keyboxActivationFailureResponse\(\)", web)

subprocess.check_call(["git", "add", "-A"])
remaining = subprocess.check_output(
    ["git", "diff", "--name-only", "--diff-filter=U"], text=True
).strip()
if remaining:
    raise SystemExit(f"unresolved merge paths remain: {remaining}")
