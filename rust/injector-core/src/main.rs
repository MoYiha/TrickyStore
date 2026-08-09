#[cfg(target_os = "android")]
fn main() {
    std::process::exit(cleverestricky_injector_core::run_cli());
}

#[cfg(not(target_os = "android"))]
fn main() {
    eprintln!("the injector executable is available only for Android");
    std::process::exit(1);
}
