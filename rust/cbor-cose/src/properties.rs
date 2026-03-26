use ahash::AHashMap;
use std::sync::RwLock;

static PROPERTIES: RwLock<Option<AHashMap<Box<str>, Box<str>>>> = RwLock::new(None);

pub fn get_property<R, F: FnOnce(&str) -> R>(name: &str, f: F) -> Option<R> {
    // Avoid panics inside Zygote
    if let Ok(cache) = PROPERTIES.read() {
        cache
            .as_ref()
            .and_then(|c| c.get(name).map(|s| f(s.as_ref())))
    } else {
        None
    }
}

pub fn set_property(name: &str, value: &str) {
    if let Ok(mut cache) = PROPERTIES.write() {
        let map = cache.get_or_insert_with(AHashMap::new);
        map.insert(Box::from(name), Box::from(value));
    }
}

pub fn clear_properties() {
    if let Ok(mut cache) = PROPERTIES.write() {
        if let Some(map) = cache.as_mut() {
            map.clear();
        }
    }
}
