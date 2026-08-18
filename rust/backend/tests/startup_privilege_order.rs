#[test]
fn backend_listener_is_created_only_after_privilege_drop() {
    let source = include_str!("../src/main.rs");
    let start = source.find("fn run() -> io::Result<()> {").expect("run function");
    let end = source[start..]
        .find("\n}\n\nfn parse_adapter_pid")
        .map(|offset| start + offset)
        .expect("run function end");
    let run = &source[start..end];
    let broker = run.find("take_broker_stream()?").expect("broker authentication");
    let harden = run.find("harden_process()?").expect("privilege drop");
    let listener = run.find("bind_abstract(BACKEND_SOCKET_NAME)?").expect("backend listener");
    assert!(broker < harden);
    assert!(harden < listener);
}
