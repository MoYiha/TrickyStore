// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#![forbid(unsafe_code)]

use std::collections::HashSet;

pub const MAX_TARGET_FILE_BYTES: usize = 1024 * 1024;
pub const MAX_TARGET_PACKAGE_RULES: usize = 2048;
pub const MAX_APP_CONFIG_BYTES: usize = 1024 * 1024;
pub const MAX_APP_CONFIG_RULES: usize = 1024;

const MAX_APP_PACKAGE_CHARS: usize = 255;
const MAX_TEMPLATE_CHARS: usize = 64;
const MAX_KEYBOX_CHARS: usize = 128;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AppPrivacyMode {
    Inherit,
    Redact,
    Isolate,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct AppConfigRule {
    pub package: String,
    pub template: Option<String>,
    pub keybox_filename: Option<String>,
    pub privacy_mode: AppPrivacyMode,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AppConfigError {
    InputTooLarge,
    TargetInputTooLarge,
    TooManyRules,
    TooManyTargetRules,
    TooManyColumns,
    InvalidPackage,
    DuplicatePackage,
    InvalidTemplate,
    InvalidKeybox,
    InvalidPrivacyMode,
    EmptyRule,
}

pub fn parse_app_config(input: &[u8]) -> Result<Vec<AppConfigRule>, AppConfigError> {
    if input.len() > MAX_APP_CONFIG_BYTES {
        return Err(AppConfigError::InputTooLarge);
    }

    // Java's InputStreamReader replaces malformed UTF-8 by default. Preserve that managed
    // compatibility while the ASCII allowlists below still reject replacement characters in
    // security-relevant fields.
    let decoded = String::from_utf8_lossy(input);
    let mut rules = Vec::new();
    let mut seen_packages = HashSet::new();
    let mut rule_count = 0usize;

    for_managed_lines(&decoded, |line| {
        if line.trim().is_empty() || line.starts_with('#') {
            return Ok(());
        }
        rule_count += 1;
        if rule_count > MAX_APP_CONFIG_RULES {
            return Err(AppConfigError::TooManyRules);
        }

        let trimmed = line.trim();
        let mut columns = trimmed.split_whitespace();
        let package = columns.next().ok_or(AppConfigError::InvalidPackage)?;
        let template_column = columns.next();
        let keybox_column = columns.next();
        let privacy_column = columns.next();
        if columns.next().is_some() {
            return Err(AppConfigError::TooManyColumns);
        }

        if !valid_app_package(package) {
            return Err(AppConfigError::InvalidPackage);
        }
        if !seen_packages.insert(package.to_owned()) {
            return Err(AppConfigError::DuplicatePackage);
        }

        let template = match template_column {
            Some("null") | None => None,
            Some(value) => {
                let lowered = value.to_lowercase();
                if !valid_template_name(&lowered) {
                    return Err(AppConfigError::InvalidTemplate);
                }
                Some(lowered)
            }
        };
        let keybox_filename = match keybox_column {
            Some("null") | None => None,
            Some(value) => {
                if !valid_app_keybox(value) {
                    return Err(AppConfigError::InvalidKeybox);
                }
                Some(value.to_owned())
            }
        };
        let privacy_mode = match privacy_column {
            None => AppPrivacyMode::Inherit,
            Some(value) if value.eq_ignore_ascii_case("inherit") => AppPrivacyMode::Inherit,
            Some(value) if value.eq_ignore_ascii_case("redact") => AppPrivacyMode::Redact,
            Some(value) if value.eq_ignore_ascii_case("isolate") => AppPrivacyMode::Isolate,
            Some(_) => return Err(AppConfigError::InvalidPrivacyMode),
        };

        if template.is_none()
            && keybox_filename.is_none()
            && privacy_mode == AppPrivacyMode::Inherit
        {
            return Err(AppConfigError::EmptyRule);
        }

        rules.push(AppConfigRule {
            package: package.to_owned(),
            template,
            keybox_filename,
            privacy_mode,
        });
        Ok(())
    })?;

    Ok(rules)
}

pub fn parse_target_packages(input: &[u8]) -> Result<Vec<String>, AppConfigError> {
    if input.len() > MAX_TARGET_FILE_BYTES {
        return Err(AppConfigError::TargetInputTooLarge);
    }

    let decoded = String::from_utf8_lossy(input);
    let mut packages = Vec::new();
    let mut rule_count = 0usize;
    for_managed_lines(&decoded, |line| {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            return Ok(());
        }
        rule_count += 1;
        if rule_count > MAX_TARGET_PACKAGE_RULES {
            return Err(AppConfigError::TooManyTargetRules);
        }

        let package = trimmed.strip_suffix('!').unwrap_or(trimmed).trim();
        if valid_target_package(package) {
            packages.push(package.to_owned());
        }
        Ok(())
    })?;
    Ok(packages)
}

fn valid_app_package(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= MAX_APP_PACKAGE_CHARS
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'.' | b'*'))
}

fn valid_template_name(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= MAX_TEMPLATE_CHARS
        && value.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(byte, b'_' | b'-')
        })
}

fn valid_app_keybox(value: &str) -> bool {
    if value.is_empty()
        || value.len() > MAX_KEYBOX_CHARS
        || value.starts_with('.')
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'.' | b'-'))
    {
        return false;
    }
    let lowered = value.to_ascii_lowercase();
    lowered.ends_with(".xml") || lowered.ends_with(".cbox")
}

fn valid_target_package(value: &str) -> bool {
    !value.is_empty()
        && value
            .chars()
            .all(|character| character.is_alphanumeric() || matches!(character, '_' | '.' | '*'))
}

fn for_managed_lines<E>(
    value: &str,
    mut callback: impl FnMut(&str) -> Result<(), E>,
) -> Result<(), E> {
    let bytes = value.as_bytes();
    let mut start = 0usize;
    let mut index = 0usize;
    while index < bytes.len() {
        if bytes[index] != b'\n' && bytes[index] != b'\r' {
            index += 1;
            continue;
        }
        callback(&value[start..index])?;
        if bytes[index] == b'\r' && bytes.get(index + 1) == Some(&b'\n') {
            index += 2;
        } else {
            index += 1;
        }
        start = index;
    }
    if start < bytes.len() {
        callback(&value[start..])?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn managed_line_splitter_handles_lf_crlf_and_lone_cr_without_terminal_empty_line() {
        let mut lines = Vec::new();
        for_managed_lines("a\nb\r\nc\rd\n", |line| {
            lines.push(line.to_owned());
            Ok::<_, ()>(())
        })
        .unwrap();
        assert_eq!(lines, ["a", "b", "c", "d"]);
    }
}
