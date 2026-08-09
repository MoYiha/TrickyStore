use std::fs::{File, OpenOptions};
use std::io::{self, Read};
use std::mem;
use std::os::unix::fs::OpenOptionsExt;

const MAXIMUM_CMDLINE_BYTES: usize = 4_096;
const MAXIMUM_MAPS_BYTES: usize = 4 * 1_024 * 1_024;
const MAXIMUM_RELEVANT_MAPPINGS: usize = 128;
const MAXIMUM_ARGUMENTS: usize = 64;
const MAXIMUM_MAGIC_LENGTH: usize = 64;
const MAGIC_ALPHABET: &[u8; 62] = b"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
const RANDOM_ACCEPTANCE_LIMIT: u8 = 248;
const O_NOFOLLOW: i32 = 0x20000;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProcessModule {
    Libc,
    Libdl,
}

impl ProcessModule {
    pub const fn file_name(self) -> &'static [u8] {
        match self {
            Self::Libc => b"libc.so",
            Self::Libdl => b"libdl.so",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ProcessMapping {
    pub module: ProcessModule,
    pub start: usize,
    pub end: usize,
    pub offset: usize,
    pub executable: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[repr(C)]
pub struct CmsgHeader {
    pub length: usize,
    pub level: i32,
    pub kind: i32,
}

const SOL_SOCKET: i32 = 1;
const SCM_RIGHTS: i32 = 1;

fn align_cmsg(length: usize) -> Option<usize> {
    let alignment = mem::size_of::<usize>();
    length
        .checked_add(alignment - 1)
        .map(|value| value & !(alignment - 1))
}

fn read_unaligned<T: Copy>(input: &[u8], offset: usize) -> Option<T> {
    let end = offset.checked_add(mem::size_of::<T>())?;
    if end > input.len() {
        return None;
    }
    Some(unsafe { input.as_ptr().add(offset).cast::<T>().read_unaligned() })
}

pub fn extract_scm_rights_fd(input: &[u8]) -> Option<i32> {
    let header_data_offset = align_cmsg(mem::size_of::<CmsgHeader>())?;
    let minimum_rights_length = header_data_offset.checked_add(mem::size_of::<i32>())?;
    let mut offset = 0usize;
    let mut received_descriptor = None;

    while input.len().saturating_sub(offset) >= mem::size_of::<CmsgHeader>() {
        let header = read_unaligned::<CmsgHeader>(input, offset)?;
        if header.length < mem::size_of::<CmsgHeader>()
            || header.length > input.len().saturating_sub(offset)
        {
            return None;
        }
        if header.level == SOL_SOCKET && header.kind == SCM_RIGHTS {
            if header.length != minimum_rights_length {
                return None;
            }
            let descriptor = read_unaligned::<i32>(input, offset + header_data_offset)?;
            if descriptor < 0 || received_descriptor.replace(descriptor).is_some() {
                return None;
            }
        }

        let step = align_cmsg(header.length)?;
        if step == 0 || step > input.len().saturating_sub(offset) {
            return None;
        }
        offset += step;
    }
    received_descriptor
}

pub fn is_supported_target_cmdline(input: &[u8]) -> bool {
    if input.is_empty()
        || input.len() >= MAXIMUM_CMDLINE_BYTES
        || input.last() != Some(&0)
        || input.iter().filter(|byte| **byte == 0).count() > MAXIMUM_ARGUMENTS
    {
        return false;
    }
    let first_end = input.iter().position(|byte| *byte == 0).unwrap_or(0);
    if first_end == 0 {
        return false;
    }
    let first = &input[..first_end];
    let basename = first.rsplit(|byte| *byte == b'/').next().unwrap_or(first);
    matches!(basename, b"keystore2" | b"com.android.phone")
}

fn read_bounded_cmdline(reader: &mut impl Read) -> io::Result<Option<Vec<u8>>> {
    let mut input = Vec::with_capacity(256);
    reader
        .take(MAXIMUM_CMDLINE_BYTES as u64)
        .read_to_end(&mut input)?;
    if input.is_empty() || input.len() >= MAXIMUM_CMDLINE_BYTES {
        Ok(None)
    } else {
        Ok(Some(input))
    }
}

pub fn validate_attached_target_cmdline(pid: i32) -> io::Result<bool> {
    if pid <= 0 {
        return Ok(false);
    }
    let path = format!("/proc/{pid}/cmdline");
    let mut file = OpenOptions::new()
        .read(true)
        .custom_flags(O_NOFOLLOW)
        .open(path)?;
    Ok(read_bounded_cmdline(&mut file)?
        .as_deref()
        .is_some_and(is_supported_target_cmdline))
}

pub fn read_relevant_process_maps(pid: Option<i32>) -> io::Result<Vec<ProcessMapping>> {
    if pid.is_some_and(|value| value <= 0) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "invalid process identifier",
        ));
    }
    let path = pid.map_or_else(
        || "/proc/self/maps".to_string(),
        |value| format!("/proc/{value}/maps"),
    );
    let mut file = OpenOptions::new()
        .read(true)
        .custom_flags(O_NOFOLLOW)
        .open(path)?;
    let mut input = Vec::with_capacity(64 * 1_024);
    file.by_ref()
        .take((MAXIMUM_MAPS_BYTES + 1) as u64)
        .read_to_end(&mut input)?;
    if input.is_empty() || input.len() > MAXIMUM_MAPS_BYTES {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "process map exceeded its allowed size",
        ));
    }
    parse_relevant_process_maps(&input).ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            "process map failed structural validation",
        )
    })
}

pub fn parse_relevant_process_maps(input: &[u8]) -> Option<Vec<ProcessMapping>> {
    if input.is_empty() || input.len() > MAXIMUM_MAPS_BYTES {
        return None;
    }
    let mut output = Vec::with_capacity(16);
    for line in input.split(|byte| *byte == b'\n') {
        let Some(mapping) = parse_relevant_mapping(line) else {
            continue;
        };
        if output.len() == MAXIMUM_RELEVANT_MAPPINGS {
            return None;
        }
        output.push(mapping);
    }
    (!output.is_empty()).then_some(output)
}

fn parse_relevant_mapping(line: &[u8]) -> Option<ProcessMapping> {
    let mut cursor = 0usize;
    let range = take_ascii_field(line, &mut cursor)?;
    let permissions = take_ascii_field(line, &mut cursor)?;
    let offset = take_ascii_field(line, &mut cursor)?;
    take_ascii_field(line, &mut cursor)?;
    take_ascii_field(line, &mut cursor)?;
    let path = take_ascii_field(line, &mut cursor)?;
    let basename = path.rsplit(|byte| *byte == b'/').next().unwrap_or(path);
    let module = match basename {
        b"libc.so" => ProcessModule::Libc,
        b"libdl.so" => ProcessModule::Libdl,
        _ => return None,
    };

    let separator = range.iter().position(|byte| *byte == b'-')?;
    let start = parse_hex_usize(&range[..separator])?;
    let end = parse_hex_usize(&range[separator + 1..])?;
    if start == 0 || start >= end || permissions.len() != 4 {
        return None;
    }
    Some(ProcessMapping {
        module,
        start,
        end,
        offset: parse_hex_usize(offset)?,
        executable: permissions[2] == b'x',
    })
}

fn take_ascii_field<'a>(input: &'a [u8], cursor: &mut usize) -> Option<&'a [u8]> {
    while input.get(*cursor).is_some_and(u8::is_ascii_whitespace) {
        *cursor += 1;
    }
    let start = *cursor;
    while input
        .get(*cursor)
        .is_some_and(|byte| !byte.is_ascii_whitespace())
    {
        *cursor += 1;
    }
    (start < *cursor).then_some(&input[start..*cursor])
}

fn parse_hex_usize(input: &[u8]) -> Option<usize> {
    if input.is_empty() || input.len() > mem::size_of::<usize>() * 2 {
        return None;
    }
    input.iter().try_fold(0usize, |current, byte| {
        let digit = match byte {
            b'0'..=b'9' => usize::from(byte - b'0'),
            b'a'..=b'f' => usize::from(byte - b'a') + 10,
            b'A'..=b'F' => usize::from(byte - b'A') + 10,
            _ => return None,
        };
        current.checked_mul(16)?.checked_add(digit)
    })
}

pub fn parse_injector_request(
    pid_value: &[u8],
    current_pid: i32,
    entry_name: &[u8],
) -> Option<i32> {
    if !matches!(entry_name, b"entry" | b"resume")
        || pid_value.is_empty()
        || pid_value.len() > 10
        || !pid_value.iter().all(u8::is_ascii_digit)
    {
        return None;
    }
    let pid = pid_value.iter().try_fold(0i32, |current, digit| {
        current
            .checked_mul(10)?
            .checked_add(i32::from(digit - b'0'))
    })?;
    (pid > 0 && pid != current_pid).then_some(pid)
}

pub fn is_safe_library_metadata(mode: u32, owner: u32) -> bool {
    const FILE_TYPE_MASK: u32 = 0o170_000;
    const REGULAR_FILE: u32 = 0o100_000;
    const GROUP_OR_OTHER_WRITE: u32 = 0o022;
    owner == 0 && mode & FILE_TYPE_MASK == REGULAR_FILE && mode & GROUP_OR_OTHER_WRITE == 0
}

pub fn generate_magic(length: usize) -> io::Result<Vec<u8>> {
    if length == 0 || length > MAXIMUM_MAGIC_LENGTH {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "invalid magic length",
        ));
    }

    let mut source = File::open("/dev/urandom")?;
    let mut output = Vec::with_capacity(length);
    let mut random = [0u8; 128];
    while output.len() < length {
        source.read_exact(&mut random)?;
        for byte in random {
            if byte < RANDOM_ACCEPTANCE_LIMIT {
                output.push(MAGIC_ALPHABET[usize::from(byte) % MAGIC_ALPHABET.len()]);
                if output.len() == length {
                    break;
                }
            }
        }
    }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn append_cmsg(buffer: &mut Vec<u8>, header: CmsgHeader, payload: &[u8]) {
        let start = buffer.len();
        let step = align_cmsg(header.length).unwrap();
        buffer.resize(start + step, 0);
        unsafe {
            buffer
                .as_mut_ptr()
                .add(start)
                .cast::<CmsgHeader>()
                .write_unaligned(header);
        }
        let data_offset = align_cmsg(mem::size_of::<CmsgHeader>()).unwrap();
        buffer[start + data_offset..start + data_offset + payload.len()].copy_from_slice(payload);
    }

    #[test]
    fn extracts_rights_after_an_unrelated_control_message() {
        let data_offset = align_cmsg(mem::size_of::<CmsgHeader>()).unwrap();
        let mut input = Vec::new();
        append_cmsg(
            &mut input,
            CmsgHeader {
                length: data_offset + 12,
                level: SOL_SOCKET,
                kind: 2,
            },
            &[0; 12],
        );
        append_cmsg(
            &mut input,
            CmsgHeader {
                length: data_offset + mem::size_of::<i32>(),
                level: SOL_SOCKET,
                kind: SCM_RIGHTS,
            },
            &41i32.to_ne_bytes(),
        );
        assert_eq!(extract_scm_rights_fd(&input), Some(41));
    }

    #[test]
    fn rejects_malformed_control_lengths() {
        let mut input = vec![0u8; mem::size_of::<CmsgHeader>()];
        let header = CmsgHeader {
            length: usize::MAX,
            level: SOL_SOCKET,
            kind: SCM_RIGHTS,
        };
        unsafe {
            input
                .as_mut_ptr()
                .cast::<CmsgHeader>()
                .write_unaligned(header)
        };
        assert_eq!(extract_scm_rights_fd(&input), None);
    }

    #[test]
    fn rejects_truncated_control_message_padding_after_rights() {
        let data_offset = align_cmsg(mem::size_of::<CmsgHeader>()).unwrap();
        let mut input = Vec::new();
        append_cmsg(
            &mut input,
            CmsgHeader {
                length: data_offset + mem::size_of::<i32>(),
                level: SOL_SOCKET,
                kind: SCM_RIGHTS,
            },
            &41i32.to_ne_bytes(),
        );
        let start = input.len();
        let header = CmsgHeader {
            length: mem::size_of::<CmsgHeader>() + 1,
            level: SOL_SOCKET,
            kind: 2,
        };
        input.resize(start + header.length, 0);
        unsafe {
            input
                .as_mut_ptr()
                .add(start)
                .cast::<CmsgHeader>()
                .write_unaligned(header);
        }
        assert_eq!(extract_scm_rights_fd(&input), None);
    }

    #[test]
    fn rejects_multiple_received_descriptors() {
        let data_offset = align_cmsg(mem::size_of::<CmsgHeader>()).unwrap();
        let mut input = Vec::new();
        for descriptor in [41i32, 42i32] {
            append_cmsg(
                &mut input,
                CmsgHeader {
                    length: data_offset + mem::size_of::<i32>(),
                    level: SOL_SOCKET,
                    kind: SCM_RIGHTS,
                },
                &descriptor.to_ne_bytes(),
            );
        }
        assert_eq!(extract_scm_rights_fd(&input), None);
    }

    #[test]
    fn rejects_a_rights_message_with_more_than_one_descriptor() {
        let data_offset = align_cmsg(mem::size_of::<CmsgHeader>()).unwrap();
        let mut input = Vec::new();
        append_cmsg(
            &mut input,
            CmsgHeader {
                length: data_offset + 2 * mem::size_of::<i32>(),
                level: SOL_SOCKET,
                kind: SCM_RIGHTS,
            },
            &[41i32.to_ne_bytes(), 42i32.to_ne_bytes()].concat(),
        );
        assert_eq!(extract_scm_rights_fd(&input), None);
    }

    #[test]
    fn validates_only_supported_stopped_process_names() {
        assert!(is_supported_target_cmdline(b"/system/bin/keystore2\0"));
        assert!(is_supported_target_cmdline(b"com.android.phone\0extra\0"));
        assert!(!is_supported_target_cmdline(
            b"/system/bin/keystore2.helper\0"
        ));
        assert!(!is_supported_target_cmdline(b"keystore2"));
    }

    #[test]
    fn reads_process_names_with_a_hard_upper_bound() {
        let mut valid = io::Cursor::new(b"/system/bin/keystore2\0".to_vec());
        let value = read_bounded_cmdline(&mut valid).unwrap().unwrap();
        assert!(is_supported_target_cmdline(&value));

        let mut oversized = io::Cursor::new(vec![b'a'; MAXIMUM_CMDLINE_BYTES]);
        assert!(read_bounded_cmdline(&mut oversized).unwrap().is_none());
    }

    #[test]
    fn validates_injector_arguments_and_library_metadata() {
        assert_eq!(parse_injector_request(b"1234", 99, b"entry"), Some(1234));
        assert_eq!(parse_injector_request(b"1234", 99, b"resume"), Some(1234));
        assert_eq!(parse_injector_request(b"99", 99, b"entry"), None);
        assert_eq!(parse_injector_request(b"12x", 99, b"entry"), None);
        assert_eq!(parse_injector_request(b"1234", 99, b"other"), None);
        assert!(is_safe_library_metadata(0o100_500, 0));
        assert!(!is_safe_library_metadata(0o100_522, 0));
        assert!(!is_safe_library_metadata(0o100_500, 2_000));
    }

    #[test]
    fn generated_magic_is_bounded_and_alphanumeric() {
        let value = generate_magic(MAXIMUM_MAGIC_LENGTH).unwrap();
        assert_eq!(value.len(), MAXIMUM_MAGIC_LENGTH);
        assert!(value.iter().all(|byte| MAGIC_ALPHABET.contains(byte)));
        assert!(generate_magic(0).is_err());
        assert!(generate_magic(MAXIMUM_MAGIC_LENGTH + 1).is_err());
    }

    #[test]
    fn parses_only_bounded_libc_and_libdl_mappings() {
        let input = b"7a00000000-7a00001000 r--p 00000000 00:01 1 /apex/lib64/libc.so\n\
                      7a00001000-7a00009000 r-xp 00001000 00:01 1 /apex/lib64/libc.so\n\
                      7b00000000-7b00001000 r--p 00000000 00:01 2 /apex/lib64/libdl.so\n\
                      7c00000000-7c00001000 r-xp 00000000 00:01 3 /system/lib64/libother.so\n";
        let mappings = parse_relevant_process_maps(input).unwrap();
        assert_eq!(mappings.len(), 3);
        assert_eq!(mappings[0].module, ProcessModule::Libc);
        assert_eq!(mappings[0].offset, 0);
        assert!(!mappings[0].executable);
        assert!(mappings[1].executable);
        assert_eq!(mappings[2].module, ProcessModule::Libdl);
    }

    #[test]
    fn rejects_oversized_or_empty_process_maps() {
        assert!(parse_relevant_process_maps(&[]).is_none());
        assert!(parse_relevant_process_maps(&vec![b'a'; MAXIMUM_MAPS_BYTES + 1]).is_none());
    }
}
