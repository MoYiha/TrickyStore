//! Automatic Pixel Beta fingerprint updater.
//!
//! Downloads and caches device fingerprints in a thread-safe in-memory store.
//! The C++ hook reads cached fingerprints directly from Rust via FFI,
//! avoiding file I/O and reducing the attack surface.
//!
//! This module is gated behind the `fingerprint` feature (enabled by default)
//! which pulls in `minreq` for lightweight HTTPS fetching.

use std::collections::HashMap;
use std::sync::RwLock;

/// Thread-safe in-memory fingerprint cache.
static CACHE: RwLock<Option<FingerprintCache>> = RwLock::new(None);

/// Default URL for fetching Pixel Beta fingerprints.
const DEFAULT_FP_URL: &str =
    "https://raw.githubusercontent.com/nicholaschum/AndroidFingerprints/main/Pixel/Beta/output.txt";

/// Internal fingerprint store.
#[derive(Debug, Clone)]
#[allow(dead_code)]
struct FingerprintCache {
    /// Map of device codename → fingerprint string.
    entries: HashMap<String, String>,
    /// URL the fingerprints were fetched from (diagnostic metadata).
    source_url: String,
    /// Timestamp (seconds since epoch) of last successful fetch (diagnostic metadata).
    fetched_at: u64,
}

/// Parse a fingerprint list from raw text.
///
/// Expects lines in the format:
/// ```text
/// google/husky/husky:15/AP41.250105.002/12731906:user/release-keys
/// ```
///
/// The device codename is extracted from the third `/`-separated segment
/// (before the `:`), e.g. `husky` from `google/husky/husky:15/...`.
pub fn parse_fingerprints(data: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for line in data.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        // Extract device codename: split by '/' to get segments
        // Optimization: Use iterator directly to avoid Vec allocation
        let mut parts = line.splitn(4, '/');
        // Skip first two segments (brand, product)
        if parts.next().is_some() && parts.next().is_some() {
            // Third segment is "device:version"
            if let Some(device_segment) = parts.next() {
                // Take before ':'
                let device = device_segment.split(':').next().unwrap_or(device_segment);
                if !device.is_empty() {
                    map.insert(device.to_string(), line.to_string());
                }
            }
        }
    }
    map
}

/// Fetch fingerprints from the given URL and update the cache.
///
/// Returns the number of fingerprints fetched, or an error message.
#[cfg(feature = "fingerprint")]
pub fn fetch_fingerprints(url: Option<&str>) -> Result<usize, String> {
    let url = url.unwrap_or(DEFAULT_FP_URL);

    let response = minreq::get(url)
        .with_timeout(10)
        .send()
        .map_err(|e| format!("HTTP request failed: {}", e))?;

    if response.status_code != 200 {
        return Err(format!("HTTP {}", response.status_code));
    }

    let body = response
        .as_str()
        .map_err(|e| format!("UTF-8 error: {}", e))?;
    let entries = parse_fingerprints(body);
    let count = entries.len();

    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);

    let cache = FingerprintCache {
        entries,
        source_url: url.to_string(),
        fetched_at: now,
    };

    if let Ok(mut guard) = CACHE.write() {
        *guard = Some(cache);
    }

    Ok(count)
}

/// Stub for when the `fingerprint` feature is disabled.
#[cfg(not(feature = "fingerprint"))]
pub fn fetch_fingerprints(_url: Option<&str>) -> Result<usize, String> {
    Err("fingerprint feature not enabled".to_string())
}

/// Look up a cached fingerprint by device codename.
///
/// Returns `None` if the cache is empty or the device is not found.
pub fn get_fingerprint(device: &str) -> Option<String> {
    let guard = CACHE.read().ok()?;
    let cache = guard.as_ref()?;
    cache.entries.get(device).cloned()
}

/// Get all cached fingerprints as a list of `"device=fingerprint"` lines.
pub fn get_all_fingerprints() -> Vec<String> {
    let guard = match CACHE.read() {
        Ok(g) => g,
        Err(_) => return vec![],
    };
    match guard.as_ref() {
        Some(cache) => cache
            .entries
            .iter()
            .map(|(k, v)| format!("{}={}", k, v))
            .collect(),
        None => vec![],
    }
}

/// Get the number of cached fingerprints.
pub fn cache_count() -> usize {
    let guard = match CACHE.read() {
        Ok(g) => g,
        Err(_) => return 0,
    };
    match guard.as_ref() {
        Some(cache) => cache.entries.len(),
        None => 0,
    }
}

/// Manually inject fingerprints into the cache (for testing or manual config).
pub fn inject_fingerprints(data: &str) -> usize {
    let entries = parse_fingerprints(data);
    let count = entries.len();

    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);

    let cache = FingerprintCache {
        entries,
        source_url: "manual".to_string(),
        fetched_at: now,
    };

    if let Ok(mut guard) = CACHE.write() {
        *guard = Some(cache);
    }

    count
}

/// Clear the fingerprint cache.
pub fn clear_cache() {
    if let Ok(mut guard) = CACHE.write() {
        *guard = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_FP_DATA: &str = "\
google/husky/husky:15/AP41.250105.002/12731906:user/release-keys
google/shiba/shiba:15/AP41.250105.002/12731906:user/release-keys
google/cheetah/cheetah:14/UP1A.231005.007/10754064:user/release-keys
# comment line
google/oriole/oriole:14/UP1A.231005.007/10754064:user/release-keys

google/redfin/redfin:13/TQ3A.230805.001/10316531:user/release-keys
";

    #[test]
    fn test_parse_fingerprints() {
        let map = parse_fingerprints(SAMPLE_FP_DATA);
        assert_eq!(map.len(), 5);
        assert!(map.contains_key("husky"));
        assert!(map.contains_key("shiba"));
        assert!(map.contains_key("cheetah"));
        assert!(map.contains_key("oriole"));
        assert!(map.contains_key("redfin"));
        assert!(map["husky"].contains("AP41.250105.002"));
    }

    #[test]
    fn test_parse_empty() {
        let map = parse_fingerprints("");
        assert!(map.is_empty());
    }

    #[test]
    fn test_parse_comments_only() {
        let map = parse_fingerprints("# this is a comment\n# another");
        assert!(map.is_empty());
    }

    #[test]
    fn test_parse_invalid_lines() {
        let map = parse_fingerprints("not-a-fingerprint\nsomething/else\n");
        // "not-a-fingerprint" has no '/', skipped
        // "something/else" has only 2 parts, skipped
        assert!(map.is_empty());
    }

    #[test]
    fn test_inject_and_get_fingerprint() {
        clear_cache();
        let count = inject_fingerprints(SAMPLE_FP_DATA);
        assert_eq!(count, 5);
        assert_eq!(cache_count(), 5);

        let fp = get_fingerprint("husky");
        assert!(fp.is_some());
        assert!(fp.unwrap().contains("husky"));

        let fp = get_fingerprint("nonexistent");
        assert!(fp.is_none());
    }

    #[test]
    fn test_get_all_fingerprints() {
        clear_cache();
        inject_fingerprints(SAMPLE_FP_DATA);
        let all = get_all_fingerprints();
        assert_eq!(all.len(), 5);
        assert!(all.iter().any(|s| s.starts_with("husky=")));
    }

    #[test]
    fn test_clear_cache() {
        inject_fingerprints(SAMPLE_FP_DATA);
        assert!(cache_count() > 0);
        clear_cache();
        assert_eq!(cache_count(), 0);
        assert!(get_fingerprint("husky").is_none());
    }

    #[test]
    fn test_cache_overwrite() {
        clear_cache();
        inject_fingerprints(SAMPLE_FP_DATA);
        assert_eq!(cache_count(), 5);

        // Overwrite with different data
        inject_fingerprints(
            "samsung/dm1q/dm1q:14/UP1A.231005.007/S901BXXU8CXK1:user/release-keys\n",
        );
        assert_eq!(cache_count(), 1);
        assert!(get_fingerprint("dm1q").is_some());
        assert!(get_fingerprint("husky").is_none());
    }

    #[test]
    fn test_parse_duplicate_device() {
        // Last line wins for duplicate device codenames
        let data = "\
google/husky/husky:15/FIRST/111:user/release-keys
google/husky/husky:15/SECOND/222:user/release-keys
";
        let map = parse_fingerprints(data);
        assert_eq!(map.len(), 1);
        assert!(map["husky"].contains("SECOND"));
    }
}
