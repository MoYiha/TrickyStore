use std::env;
use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process;
use std::thread;
use std::time::{Duration, Instant, SystemTime};

const CONFIG_DIR: &str = "/data/adb/cleverestricky";
const BRIDGE_DIR: &str = "/data/adb/cleverestricky/webui_bridge";
const REQUEST_DIR: &str = "/data/adb/cleverestricky/webui_bridge/requests";
const RESPONSE_DIR: &str = "/data/adb/cleverestricky/webui_bridge/responses";
const STAGING_DIR: &str = "/data/adb/cleverestricky/webui_bridge/staging";
const MAX_REQUEST_BYTES: usize = 1024 * 1024;
const MAX_UPLOAD_BYTES: usize = 20 * 1024 * 1024;
const MAX_DOWNLOAD_BYTES: usize = 20 * 1024 * 1024;
const MAX_RESPONSE_ENVELOPE_BYTES: usize = 512 * 1024;
const MAX_CHUNK_BYTES: usize = 64 * 1024;
const STALE_AGE: Duration = Duration::from_secs(10 * 60);
const CLAIM_TIMEOUT: Duration = Duration::from_secs(5);
const O_NOFOLLOW: i32 = 0x20000;

fn main() {
    if let Err(error) = run() {
        eprintln!("{error}");
        process::exit(1);
    }
}

fn run() -> Result<(), String> {
    ensure_layout()?;
    cleanup_stale();
    let args: Vec<String> = env::args().skip(1).collect();
    let command = args.first().map(String::as_str).ok_or("Missing command")?;
    match command {
        "call" if args.len() == 3 => {
            let request = decode_base64url(&args[1], MAX_REQUEST_BYTES)?;
            call(request, parse_timeout(&args[2])?)
        }
        "call-file" if args.len() == 3 => {
            let id = validate_id(&args[1])?;
            let path = stage_path(id, "request")?;
            let request_result = read_limited(&path, MAX_REQUEST_BYTES);
            remove_if_regular(&path);
            let request = request_result?;
            call(request, parse_timeout(&args[2])?)
        }
        "stage-create" if args.len() == 2 => stage_create(&args[1]),
        "stage-append" if args.len() == 4 => stage_append(&args[1], &args[2], &args[3]),
        "stage-read" if args.len() == 5 => stage_read(&args[1], &args[2], &args[3], &args[4]),
        "stage-drop" if args.len() == 3 => stage_drop(&args[1], &args[2]),
        "export" if args.len() == 4 => export_file(&args[1], &args[2], &args[3]),
        _ => Err("Invalid command".to_string()),
    }
}

fn ensure_layout() -> Result<(), String> {
    ensure_existing_directory(Path::new(CONFIG_DIR))?;
    ensure_directory(Path::new(BRIDGE_DIR))?;
    ensure_directory(Path::new(REQUEST_DIR))?;
    ensure_directory(Path::new(RESPONSE_DIR))?;
    ensure_directory(Path::new(STAGING_DIR))
}

fn ensure_existing_directory(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|error| format!("Configuration directory is unavailable: {error}"))?;
    if metadata.file_type().is_symlink() || !metadata.is_dir() {
        return Err("Configuration directory is unsafe".to_string());
    }
    Ok(())
}

fn ensure_directory(path: &Path) -> Result<(), String> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.file_type().is_symlink() || !metadata.is_dir() => {
            return Err("Bridge path is unsafe".to_string());
        }
        Ok(_) => {}
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            fs::create_dir(path).map_err(|create_error| {
                format!("Could not create bridge directory: {create_error}")
            })?;
        }
        Err(error) => return Err(format!("Could not inspect bridge directory: {error}")),
    }
    fs::set_permissions(path, fs::Permissions::from_mode(0o700))
        .map_err(|error| format!("Could not secure bridge directory: {error}"))
}

fn parse_timeout(value: &str) -> Result<Duration, String> {
    let milliseconds = value.parse::<u64>().map_err(|_| "Invalid timeout")?;
    if !(1000..=120_000).contains(&milliseconds) {
        return Err("Invalid timeout".to_string());
    }
    Ok(Duration::from_millis(milliseconds))
}

fn random_id() -> Result<String, String> {
    let mut random = [0u8; 16];
    File::open("/dev/urandom")
        .and_then(|mut file| file.read_exact(&mut random))
        .map_err(|error| format!("Secure randomness is unavailable: {error}"))?;
    let alphabet = b"0123456789abcdef";
    let mut output = String::with_capacity(32);
    for byte in random {
        output.push(alphabet[(byte >> 4) as usize] as char);
        output.push(alphabet[(byte & 0x0f) as usize] as char);
    }
    Ok(output)
}

fn validate_id(value: &str) -> Result<&str, String> {
    if value.len() == 32
        && value
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
    {
        Ok(value)
    } else {
        Err("Invalid staging identifier".to_string())
    }
}

fn validate_kind(value: &str) -> Result<(&str, usize), String> {
    match value {
        "request" => Ok(("request", MAX_REQUEST_BYTES)),
        "upload" => Ok(("upload", MAX_UPLOAD_BYTES)),
        "download" => Ok(("download", MAX_DOWNLOAD_BYTES)),
        "export" => Ok(("export", MAX_DOWNLOAD_BYTES)),
        _ => Err("Invalid staging kind".to_string()),
    }
}

fn stage_path(id: &str, kind: &str) -> Result<PathBuf, String> {
    validate_id(id)?;
    let (extension, _) = validate_kind(kind)?;
    Ok(Path::new(STAGING_DIR).join(format!("{id}.{extension}")))
}

fn request_path(id: &str) -> PathBuf {
    Path::new(REQUEST_DIR).join(format!("{id}.request"))
}

fn response_path(id: &str) -> PathBuf {
    Path::new(RESPONSE_DIR).join(format!("{id}.response"))
}

fn call(request: Vec<u8>, timeout: Duration) -> Result<(), String> {
    if request.is_empty() || request.len() > MAX_REQUEST_BYTES {
        return Err("Request size is outside the supported range".to_string());
    }
    let request_text = std::str::from_utf8(&request)
        .map_err(|_| "Request is not valid UTF-8")?
        .trim();
    if !request_text.starts_with('{') || !request_text.ends_with('}') {
        return Err("Request envelope is invalid".to_string());
    }
    let id = random_id()?;
    let request_file = request_path(&id);
    let response_file = response_path(&id);
    atomic_write(&request_file, &request)?;
    let deadline = Instant::now() + timeout;
    let claim_deadline = (Instant::now() + CLAIM_TIMEOUT).min(deadline);
    let mut claimed = false;
    let mut wait = Duration::from_millis(10);
    while Instant::now() < deadline {
        match fs::symlink_metadata(&response_file) {
            Ok(metadata) if metadata.file_type().is_symlink() || !metadata.is_file() => {
                remove_if_regular(&request_file);
                return Err("Bridge response path is unsafe".to_string());
            }
            Ok(_) => {
                let response_result = read_limited(&response_file, MAX_RESPONSE_ENVELOPE_BYTES);
                remove_if_regular(&response_file);
                remove_if_regular(&request_file);
                let response = response_result?;
                let text = String::from_utf8(response)
                    .map_err(|_| "Bridge response is not valid UTF-8")?;
                print!("{text}");
                return Ok(());
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => {
                remove_if_regular(&request_file);
                return Err(format!("Could not inspect bridge response: {error}"));
            }
        }
        if !claimed {
            match fs::symlink_metadata(&request_file) {
                Ok(metadata) if metadata.file_type().is_symlink() || !metadata.is_file() => {
                    remove_if_regular(&request_file);
                    return Err("Bridge request path is unsafe".to_string());
                }
                Ok(_) if Instant::now() >= claim_deadline => {
                    remove_if_regular(&request_file);
                    return Err("Native WebUI service is unavailable".to_string());
                }
                Ok(_) => {}
                Err(error) if error.kind() == io::ErrorKind::NotFound => claimed = true,
                Err(error) => {
                    remove_if_regular(&request_file);
                    return Err(format!("Could not inspect bridge request: {error}"));
                }
            }
        }
        thread::sleep(wait);
        wait = (wait + Duration::from_millis(5)).min(Duration::from_millis(50));
    }
    remove_if_regular(&request_file);
    Err("WebUI service request timed out".to_string())
}

fn stage_create(kind: &str) -> Result<(), String> {
    let (extension, _) = validate_kind(kind)?;
    if extension == "download" {
        return Err("Download stages are service-owned".to_string());
    }
    for _ in 0..8 {
        let id = random_id()?;
        let path = stage_path(&id, extension)?;
        match OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&path)
        {
            Ok(_) => {
                println!("{id}");
                return Ok(());
            }
            Err(error) if error.kind() == io::ErrorKind::AlreadyExists => continue,
            Err(error) => return Err(format!("Could not create staging file: {error}")),
        }
    }
    Err("Could not allocate staging identifier".to_string())
}

fn stage_append(kind: &str, id: &str, encoded: &str) -> Result<(), String> {
    let (_, limit) = validate_kind(kind)?;
    if kind == "download" {
        return Err("Download stages are service-owned".to_string());
    }
    let chunk = decode_base64url(encoded, MAX_CHUNK_BYTES)?;
    if chunk.is_empty() {
        return Err("Empty staging chunk".to_string());
    }
    let path = stage_path(validate_id(id)?, kind)?;
    let mut file = OpenOptions::new()
        .append(true)
        .custom_flags(O_NOFOLLOW)
        .open(&path)
        .map_err(|error| format!("Could not open staging file: {error}"))?;
    let metadata = file
        .metadata()
        .map_err(|error| format!("Could not inspect staging file: {error}"))?;
    if !metadata.is_file() {
        return Err("Staging path is unsafe".to_string());
    }
    if metadata.len() > limit as u64 || chunk.len() as u64 > limit as u64 - metadata.len() {
        return Err("Staging file exceeds its size limit".to_string());
    }
    file.write_all(&chunk)
        .map_err(|error| format!("Could not append staging data: {error}"))
}

fn stage_read(kind: &str, id: &str, offset_value: &str, length_value: &str) -> Result<(), String> {
    if kind != "download" {
        return Err("Only download stages can be read".to_string());
    }
    let offset = offset_value
        .parse::<u64>()
        .map_err(|_| "Invalid read offset")?;
    let length = length_value
        .parse::<usize>()
        .map_err(|_| "Invalid read length")?;
    if length == 0 || length > MAX_CHUNK_BYTES {
        return Err("Invalid read length".to_string());
    }
    let path = stage_path(validate_id(id)?, kind)?;
    let mut file = OpenOptions::new()
        .read(true)
        .custom_flags(O_NOFOLLOW)
        .open(&path)
        .map_err(|error| format!("Could not open staged response: {error}"))?;
    let metadata = file
        .metadata()
        .map_err(|error| format!("Could not inspect staged response: {error}"))?;
    if !metadata.is_file() {
        return Err("Staging path is unsafe".to_string());
    }
    if metadata.len() > MAX_DOWNLOAD_BYTES as u64
        || offset > metadata.len()
        || length as u64 > metadata.len() - offset
    {
        return Err("Staged read is outside the file".to_string());
    }
    file.seek(SeekFrom::Start(offset))
        .map_err(|error| format!("Could not seek staged response: {error}"))?;
    let mut bytes = vec![0u8; length];
    file.read_exact(&mut bytes)
        .map_err(|error| format!("Could not read staged response: {error}"))?;
    print!("{}", encode_base64url(&bytes));
    Ok(())
}

fn stage_drop(kind: &str, id: &str) -> Result<(), String> {
    validate_kind(kind)?;
    let path = stage_path(validate_id(id)?, kind)?;
    match fs::symlink_metadata(&path) {
        Ok(metadata) if metadata.is_file() && !metadata.file_type().is_symlink() => {
            fs::remove_file(path).map_err(|error| format!("Could not remove staging file: {error}"))
        }
        Ok(_) => Err("Staging path is unsafe".to_string()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(format!("Could not inspect staging file: {error}")),
    }
}

fn export_file(kind: &str, id: &str, encoded_name: &str) -> Result<(), String> {
    let (_, limit) = validate_kind(kind)?;
    if kind == "request" {
        return Err("Request stages cannot be exported".to_string());
    }
    let source_path = stage_path(validate_id(id)?, kind)?;
    let mut source = OpenOptions::new()
        .read(true)
        .custom_flags(O_NOFOLLOW)
        .open(&source_path)
        .map_err(|error| format!("Could not open export source: {error}"))?;
    let source_metadata = source
        .metadata()
        .map_err(|error| format!("Could not inspect export source: {error}"))?;
    if !source_metadata.is_file() {
        return Err("Export source is unsafe".to_string());
    }
    if source_metadata.len() == 0 || source_metadata.len() > limit as u64 {
        return Err("Export source size is outside the supported range".to_string());
    }
    let filename_bytes = decode_base64url(encoded_name, 256)?;
    let filename =
        String::from_utf8(filename_bytes).map_err(|_| "Download filename is not valid UTF-8")?;
    validate_filename(&filename)?;
    let download_dir = select_download_dir()?;
    let (destination, mut output) = create_export_destination(&download_dir, &filename)?;
    let created_metadata = output
        .metadata()
        .map_err(|error| format!("Could not inspect download descriptor: {error}"))?;
    let export_result = (|| -> Result<(), String> {
        copy_bounded(&mut source, &mut output, limit as u64)?;
        output
            .set_permissions(fs::Permissions::from_mode(0o644))
            .map_err(|error| format!("Could not set download permissions: {error}"))?;
        output
            .sync_all()
            .map_err(|error| format!("Could not persist download: {error}"))?;
        let path_metadata = safe_file_metadata(&destination)?;
        if created_metadata.dev() != path_metadata.dev()
            || created_metadata.ino() != path_metadata.ino()
        {
            return Err("Download destination changed during export".to_string());
        }
        Ok(())
    })();
    if let Err(error) = export_result {
        drop(output);
        remove_if_same_file(&destination, &created_metadata);
        return Err(error);
    }
    drop(output);
    remove_if_regular(&source_path);
    println!("{}", destination.display());
    Ok(())
}

fn select_download_dir() -> Result<PathBuf, String> {
    let candidate = Path::new("/storage/emulated/0/Download");
    if let Ok(metadata) = fs::symlink_metadata(candidate) {
        if metadata.is_dir() && !metadata.file_type().is_symlink() {
            return Ok(candidate.to_path_buf());
        }
    }
    Err("Android Download directory is unavailable".to_string())
}

fn validate_filename(filename: &str) -> Result<(), String> {
    if filename.is_empty()
        || filename.len() > 128
        || filename.starts_with('.')
        || filename.chars().any(char::is_control)
    {
        return Err("Invalid download filename".to_string());
    }
    let path = Path::new(filename);
    if path.file_name().and_then(|value| value.to_str()) != Some(filename)
        || filename == "."
        || filename == ".."
    {
        return Err("Invalid download filename".to_string());
    }
    Ok(())
}

fn create_export_destination(directory: &Path, filename: &str) -> Result<(PathBuf, File), String> {
    for attempt in 0..16 {
        let name = if attempt == 0 {
            filename.to_string()
        } else {
            suffixed_filename(filename, &random_id()?[..8])
        };
        let path = directory.join(name);
        match OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&path)
        {
            Ok(file) => return Ok((path, file)),
            Err(error) if error.kind() == io::ErrorKind::AlreadyExists => continue,
            Err(error) => return Err(format!("Could not create download: {error}")),
        }
    }
    Err("Could not allocate a unique download filename".to_string())
}

fn suffixed_filename(filename: &str, suffix: &str) -> String {
    let path = Path::new(filename);
    let stem = path
        .file_stem()
        .and_then(|value| value.to_str())
        .unwrap_or("download");
    match path.extension().and_then(|value| value.to_str()) {
        Some(extension) if !extension.is_empty() => format!("{stem}_{suffix}.{extension}"),
        _ => format!("{stem}_{suffix}"),
    }
}

fn copy_bounded(input: &mut File, output: &mut File, limit: u64) -> Result<(), String> {
    let copied = io::copy(&mut Read::by_ref(input).take(limit + 1), output)
        .map_err(|error| format!("Could not export download: {error}"))?;
    if copied > limit {
        return Err("Export exceeded its size limit".to_string());
    }
    Ok(())
}

fn safe_file_metadata(path: &Path) -> Result<fs::Metadata, String> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|error| format!("Could not inspect staging file: {error}"))?;
    if metadata.file_type().is_symlink() || !metadata.is_file() {
        return Err("Staging path is unsafe".to_string());
    }
    Ok(metadata)
}

fn atomic_write(path: &Path, content: &[u8]) -> Result<(), String> {
    if fs::symlink_metadata(path).is_ok() {
        return Err("Bridge destination already exists".to_string());
    }
    let parent = path.parent().ok_or("Bridge destination has no parent")?;
    let temporary = parent.join(format!(".{}.tmp", random_id()?));
    let result = (|| -> Result<(), String> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&temporary)
            .map_err(|error| format!("Could not create bridge request: {error}"))?;
        file.write_all(content)
            .map_err(|error| format!("Could not write bridge request: {error}"))?;
        file.sync_all()
            .map_err(|error| format!("Could not persist bridge request: {error}"))?;
        fs::rename(&temporary, path)
            .map_err(|error| format!("Could not publish bridge request: {error}"))
    })();
    if result.is_err() {
        remove_if_regular(&temporary);
    }
    result
}

fn read_limited(path: &Path, limit: usize) -> Result<Vec<u8>, String> {
    let mut file = OpenOptions::new()
        .read(true)
        .custom_flags(O_NOFOLLOW)
        .open(path)
        .map_err(|error| format!("Could not open file: {error}"))?;
    let metadata = file
        .metadata()
        .map_err(|error| format!("Could not inspect file: {error}"))?;
    if !metadata.is_file() {
        return Err("File path is unsafe".to_string());
    }
    if metadata.len() > limit as u64 {
        return Err("File exceeds its size limit".to_string());
    }
    let mut output = Vec::with_capacity(metadata.len() as usize);
    Read::by_ref(&mut file)
        .take(limit as u64 + 1)
        .read_to_end(&mut output)
        .map_err(|error| format!("Could not read file: {error}"))?;
    if output.len() > limit {
        return Err("File exceeds its size limit".to_string());
    }
    Ok(output)
}

fn remove_if_regular(path: &Path) {
    if let Ok(metadata) = fs::symlink_metadata(path) {
        if metadata.is_file() && !metadata.file_type().is_symlink() {
            let _ = fs::remove_file(path);
        }
    }
}

fn remove_if_same_file(path: &Path, expected: &fs::Metadata) {
    if let Ok(metadata) = fs::symlink_metadata(path) {
        if metadata.is_file()
            && !metadata.file_type().is_symlink()
            && metadata.dev() == expected.dev()
            && metadata.ino() == expected.ino()
        {
            let _ = fs::remove_file(path);
        }
    }
}

fn cleanup_stale() {
    for directory in [REQUEST_DIR, RESPONSE_DIR, STAGING_DIR] {
        let Ok(entries) = fs::read_dir(directory) else {
            continue;
        };
        for entry in entries.flatten().take(1024) {
            let path = entry.path();
            let Ok(metadata) = fs::symlink_metadata(&path) else {
                continue;
            };
            if metadata.file_type().is_symlink() || !metadata.is_file() {
                continue;
            }
            let stale = metadata
                .modified()
                .ok()
                .and_then(|modified| SystemTime::now().duration_since(modified).ok())
                .is_some_and(|age| age > STALE_AGE);
            if stale {
                let _ = fs::remove_file(path);
            }
        }
    }
}

fn decode_base64url(value: &str, limit: usize) -> Result<Vec<u8>, String> {
    if value.len() > limit.saturating_mul(4).saturating_add(2) / 3 + 4 || value.len() % 4 == 1 {
        return Err("Encoded payload exceeds its size limit".to_string());
    }
    let mut output = Vec::with_capacity(value.len().saturating_mul(3) / 4);
    let mut buffer = 0u32;
    let mut bits = 0u32;
    for byte in value.bytes() {
        let decoded = match byte {
            b'A'..=b'Z' => byte - b'A',
            b'a'..=b'z' => byte - b'a' + 26,
            b'0'..=b'9' => byte - b'0' + 52,
            b'-' => 62,
            b'_' => 63,
            _ => return Err("Invalid base64url payload".to_string()),
        };
        buffer = (buffer << 6) | decoded as u32;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            output.push(((buffer >> bits) & 0xff) as u8);
            buffer = if bits == 0 {
                0
            } else {
                buffer & ((1u32 << bits) - 1)
            };
            if output.len() > limit {
                return Err("Decoded payload exceeds its size limit".to_string());
            }
        }
    }
    if buffer != 0 {
        return Err("Invalid base64url padding bits".to_string());
    }
    Ok(output)
}

fn encode_base64url(bytes: &[u8]) -> String {
    let alphabet = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    let mut output = String::with_capacity((bytes.len() * 4).div_ceil(3));
    let mut index = 0;
    while index + 3 <= bytes.len() {
        let value = ((bytes[index] as u32) << 16)
            | ((bytes[index + 1] as u32) << 8)
            | bytes[index + 2] as u32;
        output.push(alphabet[((value >> 18) & 63) as usize] as char);
        output.push(alphabet[((value >> 12) & 63) as usize] as char);
        output.push(alphabet[((value >> 6) & 63) as usize] as char);
        output.push(alphabet[(value & 63) as usize] as char);
        index += 3;
    }
    let remaining = bytes.len() - index;
    if remaining == 1 {
        let value = (bytes[index] as u32) << 16;
        output.push(alphabet[((value >> 18) & 63) as usize] as char);
        output.push(alphabet[((value >> 12) & 63) as usize] as char);
    } else if remaining == 2 {
        let value = ((bytes[index] as u32) << 16) | ((bytes[index + 1] as u32) << 8);
        output.push(alphabet[((value >> 18) & 63) as usize] as char);
        output.push(alphabet[((value >> 12) & 63) as usize] as char);
        output.push(alphabet[((value >> 6) & 63) as usize] as char);
    }
    output
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base64url_round_trip() {
        for value in [
            b"".as_slice(),
            b"f".as_slice(),
            b"fo".as_slice(),
            b"foo".as_slice(),
            b"\0\xffnative bridge".as_slice(),
        ] {
            let encoded = encode_base64url(value);
            assert_eq!(decode_base64url(&encoded, value.len()).unwrap(), value);
        }
    }

    #[test]
    fn base64url_rejects_noncanonical_input() {
        assert!(decode_base64url("A", 32).is_err());
        assert!(decode_base64url("AB", 32).is_err());
        assert!(decode_base64url("Zm9v=", 32).is_err());
        assert!(decode_base64url("Zm9v+", 32).is_err());
    }

    #[test]
    fn identifiers_and_filenames_are_bounded() {
        assert!(validate_id("0123456789abcdef0123456789abcdef").is_ok());
        assert!(validate_id("0123456789ABCDEF0123456789ABCDEF").is_err());
        assert!(validate_id("../../etc/passwd").is_err());
        assert!(validate_filename("CleveresTricky-backup.zip").is_ok());
        assert!(validate_filename("../backup.zip").is_err());
        assert!(validate_filename(".hidden").is_err());
        assert_eq!(suffixed_filename("backup.zip", "1234"), "backup_1234.zip");
    }
}
