// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_policy_config_core::{
    parse_app_config, parse_target_packages, AppConfigError, AppPrivacyMode, MAX_APP_CONFIG_BYTES,
    MAX_APP_CONFIG_RULES, MAX_TARGET_FILE_BYTES, MAX_TARGET_PACKAGE_RULES,
};

#[test]
fn app_config_preserves_columns_case_and_null_semantics() {
    let input = b"com.example.* PIXEL keybox.CBOX ReDaCt\r\ncom.second null second.xml isolate\n";
    let parsed = parse_app_config(input).expect("valid app_config");
    assert_eq!(parsed.len(), 2);
    assert_eq!(parsed[0].package, "com.example.*");
    assert_eq!(parsed[0].template.as_deref(), Some("pixel"));
    assert_eq!(parsed[0].keybox_filename.as_deref(), Some("keybox.CBOX"));
    assert_eq!(parsed[0].privacy_mode, AppPrivacyMode::Redact);
    assert_eq!(parsed[1].template, None);
    assert_eq!(parsed[1].keybox_filename.as_deref(), Some("second.xml"));
    assert_eq!(parsed[1].privacy_mode, AppPrivacyMode::Isolate);
}

#[test]
fn app_config_only_treats_column_zero_hash_as_comment() {
    assert!(parse_app_config(b"# comment\ncom.example pixel null inherit\n").is_ok());
    assert_eq!(
        parse_app_config(b"  # comment\ncom.example pixel null inherit\n"),
        Err(AppConfigError::InvalidPackage),
    );
}

#[test]
fn app_config_rejects_empty_duplicate_invalid_and_extra_column_rules() {
    assert_eq!(
        parse_app_config(b"com.example null null inherit\n"),
        Err(AppConfigError::EmptyRule),
    );
    assert_eq!(
        parse_app_config(b"com.example pixel null inherit\ncom.example other null inherit\n"),
        Err(AppConfigError::DuplicatePackage),
    );
    assert_eq!(
        parse_app_config(b"com.example ../bad null inherit\n"),
        Err(AppConfigError::InvalidTemplate),
    );
    assert_eq!(
        parse_app_config(b"com.example pixel .secret.xml inherit\n"),
        Err(AppConfigError::InvalidKeybox),
    );
    assert_eq!(
        parse_app_config(b"com.example pixel null unknown\n"),
        Err(AppConfigError::InvalidPrivacyMode),
    );
    assert_eq!(
        parse_app_config(b"com.example pixel null inherit extra\n"),
        Err(AppConfigError::TooManyColumns),
    );
}

#[test]
fn app_config_preserves_case_sensitive_literal_null_behavior() {
    let parsed =
        parse_app_config(b"com.example NULL null inherit\n").expect("uppercase NULL is a template");
    assert_eq!(parsed[0].template.as_deref(), Some("null"));
}

#[test]
fn app_config_enforces_ascii_package_and_bounded_lengths() {
    let package = "a".repeat(255);
    let input = format!("{package} pixel null inherit\n");
    assert!(parse_app_config(input.as_bytes()).is_ok());

    let package = "a".repeat(256);
    let input = format!("{package} pixel null inherit\n");
    assert_eq!(
        parse_app_config(input.as_bytes()),
        Err(AppConfigError::InvalidPackage)
    );
    assert_eq!(
        parse_app_config("cöm.example pixel null inherit\n".as_bytes()),
        Err(AppConfigError::InvalidPackage),
    );
}

#[test]
fn app_config_rule_and_byte_limits_are_fail_closed() {
    let mut input = String::new();
    for index in 0..MAX_APP_CONFIG_RULES {
        input.push_str(&format!("com.example{index} pixel null inherit\n"));
    }
    assert_eq!(
        parse_app_config(input.as_bytes()).unwrap().len(),
        MAX_APP_CONFIG_RULES
    );
    input.push_str("com.overflow pixel null inherit\n");
    assert_eq!(
        parse_app_config(input.as_bytes()),
        Err(AppConfigError::TooManyRules)
    );
    assert_eq!(
        parse_app_config(&vec![b'a'; MAX_APP_CONFIG_BYTES + 1]),
        Err(AppConfigError::InputTooLarge),
    );
}

#[test]
fn target_parser_trims_comments_suffix_and_ignores_invalid_entries() {
    let input = "  # comment\r\ncom.example!\n bad/name \rcom.second.*   !  \ninvalid!!\n";
    let parsed = parse_target_packages(input.as_bytes()).expect("bounded target file");
    assert_eq!(parsed, vec!["com.example", "com.second.*"]);
}

#[test]
fn target_parser_preserves_unicode_letter_digit_acceptance() {
    let parsed =
        parse_target_packages("cöm.example\n包.名\n".as_bytes()).expect("unicode target entries");
    assert_eq!(parsed, vec!["cöm.example", "包.名"]);
}

#[test]
fn target_invalid_lines_still_count_toward_rule_limit() {
    let mut input = String::new();
    for _ in 0..MAX_TARGET_PACKAGE_RULES {
        input.push_str("bad/name\n");
    }
    assert!(parse_target_packages(input.as_bytes()).unwrap().is_empty());
    input.push_str("com.valid\n");
    assert_eq!(
        parse_target_packages(input.as_bytes()),
        Err(AppConfigError::TooManyTargetRules),
    );
}

#[test]
fn target_parser_enforces_only_the_file_bound_not_package_length() {
    let long_package = "a".repeat(300);
    let parsed = parse_target_packages(format!("{long_package}\n").as_bytes())
        .expect("managed-compatible long target");
    assert_eq!(parsed, vec![long_package]);
    assert_eq!(
        parse_target_packages(&vec![b'a'; MAX_TARGET_FILE_BYTES + 1]),
        Err(AppConfigError::TargetInputTooLarge),
    );
}

#[test]
fn malformed_utf8_uses_managed_replacement_behavior_and_fails_relevant_rule() {
    let mut input = b"com.example pixel null inherit\n".to_vec();
    input.splice(4..4, [0xff]);
    assert_eq!(
        parse_app_config(&input),
        Err(AppConfigError::InvalidPackage)
    );
}
