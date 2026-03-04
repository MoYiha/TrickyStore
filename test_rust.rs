use std::sync::RwLock;
use std::collections::HashMap;

static CACHE: RwLock<HashMap<String, String>> = RwLock::new(HashMap::new());

fn main() {
    println!("Works");
}
