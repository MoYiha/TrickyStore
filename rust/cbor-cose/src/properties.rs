use ahash::AHashMap;
use std::sync::{OnceLock, RwLock};

static PROPERTIES: OnceLock<RwLock<AHashMap<String, String>>> = OnceLock::new();

fn get_cache() -> &'static RwLock<AHashMap<String, String>> {
    PROPERTIES.get_or_init(|| RwLock::new(AHashMap::new()))
}

pub fn get_property(name: &str) -> Option<String> {
    // Avoid panics inside Zygote
    if let Ok(cache) = get_cache().read() {
        cache.get(name).cloned()
    } else {
        None
    }
}

pub fn set_property(name: &str, value: &str) {
    if let Ok(mut cache) = get_cache().write() {
        cache.insert(name.to_string(), value.to_string());
    }
}

pub fn clear_properties() {
    if let Ok(mut cache) = get_cache().write() {
        cache.clear();
    }
}
