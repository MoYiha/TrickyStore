use crate::abi::InjectorSymbols;
use crate::process_memory::read_process_memory;
use cleverestricky_native_core::injector_support::{
    process_image_base, read_relevant_process_maps, unique_process_image, ProcessImageId,
    ProcessMapping, ProcessModule,
};
use std::ffi::{c_char, c_int, c_void, CString};

const RTLD_NOW: c_int = 2;
const MAXIMUM_LIBRARY_OFFSET: usize = 512 * 1_024 * 1_024;
const ELF_HEADER_SIZE: usize = 64;
const ELF_PROGRAM_HEADER_SIZE: usize = 56;
const MAXIMUM_ELF_PROGRAM_HEADERS: usize = 128;
const MAXIMUM_ELF_PROGRAM_HEADER_OFFSET: usize = 64 * 1_024;
const MAXIMUM_ELF_PROGRAM_HEADER_TABLE_BYTES: usize = 64 * 1_024;
const MAXIMUM_ELF_NOTE_BYTES: usize = 64 * 1_024;
const MAXIMUM_BUILD_ID_BYTES: usize = 64;
const ELF_CLASS_64: u8 = 2;
const ELF_DATA_LITTLE_ENDIAN: u8 = 1;
const ELF_VERSION_CURRENT: u8 = 1;
const ELF_TYPE_SHARED_OBJECT: u16 = 3;
const ELF_MACHINE_X86_64: u16 = 62;
const ELF_MACHINE_AARCH64: u16 = 183;
const PROGRAM_HEADER_NOTE: u32 = 4;
const GNU_BUILD_ID_NOTE: u32 = 3;
const GNU_NOTE_NAME: &[u8] = b"GNU\0";

type VerifiedImagePair = (ProcessModule, ProcessImageId, ProcessImageId);

#[derive(Debug, Eq, PartialEq)]
struct ElfImageIdentity {
    machine: u16,
    flags: u32,
    build_id: Vec<u8>,
}

#[link(name = "dl")]
extern "C" {
    fn dlopen(file_name: *const c_char, flags: c_int) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    fn dlclose(handle: *mut c_void) -> c_int;
}

struct LibraryHandle(*mut c_void);

impl LibraryHandle {
    fn open(module: ProcessModule) -> Result<Self, String> {
        let name = CString::new(module.file_name())
            .map_err(|_| "platform library name contained a null byte".to_string())?;
        let handle = unsafe { dlopen(name.as_ptr(), RTLD_NOW) };
        if handle.is_null() {
            Err(format!(
                "could not open platform library {}",
                String::from_utf8_lossy(module.file_name())
            ))
        } else {
            Ok(Self(handle))
        }
    }
}

impl Drop for LibraryHandle {
    fn drop(&mut self) {
        if !self.0.is_null() {
            unsafe {
                dlclose(self.0);
            }
            self.0 = std::ptr::null_mut();
        }
    }
}

pub(crate) fn resolve_injector_symbols(pid: i32) -> Result<InjectorSymbols, String> {
    if pid <= 0 {
        return Err("invalid target process for symbol resolution".into());
    }
    let local = read_relevant_process_maps(None)
        .map_err(|error| format!("could not read local process mappings: {error}"))?;
    let remote = read_relevant_process_maps(Some(pid))
        .map_err(|error| format!("could not read target process mappings: {error}"))?;
    let libc = LibraryHandle::open(ProcessModule::Libc)?;
    let libdl = LibraryHandle::open(ProcessModule::Libdl)?;
    let mut verified_images = Vec::with_capacity(2);

    let (close_address, libc_image) = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"close",
    )?;
    let mut output = InjectorSymbols {
        libc_return: remote
            .iter()
            .find(|mapping| {
                mapping.module == ProcessModule::Libc
                    && mapping.image == libc_image
                    && !mapping.executable
            })
            .map(|mapping| mapping.start)
            .filter(|address| *address != 0)
            .ok_or_else(|| "target libc has no controlled return trap mapping".to_string())?,
        close: close_address,
        ..InjectorSymbols::default()
    };

    output.socket = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"socket",
    )?
    .0;
    output.bind = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"bind",
    )?
    .0;
    output.recvmsg = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"recvmsg",
    )?
    .0;
    output.mmap = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"mmap",
    )?
    .0;
    output.munmap = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"munmap",
    )?
    .0;
    output.errno_location = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"__errno",
    )
    .map(|resolved| resolved.0)
    .unwrap_or(0);
    output.android_dlopen_ext = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libdl,
        &libdl,
        b"android_dlopen_ext",
    )?
    .0;
    output.dlerror = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libdl,
        &libdl,
        b"dlerror",
    )
    .map(|resolved| resolved.0)
    .unwrap_or(0);
    output.strlen = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libc,
        &libc,
        b"strlen",
    )
    .map(|resolved| resolved.0)
    .unwrap_or(0);
    output.dlsym = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libdl,
        &libdl,
        b"dlsym",
    )?
    .0;
    output.dlclose = resolve_symbol(
        pid,
        &local,
        &remote,
        &mut verified_images,
        ProcessModule::Libdl,
        &libdl,
        b"dlclose",
    )?
    .0;
    Ok(output)
}

fn resolve_symbol(
    pid: i32,
    local_mappings: &[ProcessMapping],
    remote_mappings: &[ProcessMapping],
    verified_images: &mut Vec<VerifiedImagePair>,
    module: ProcessModule,
    library: &LibraryHandle,
    symbol: &[u8],
) -> Result<(usize, ProcessImageId), String> {
    let symbol_name = CString::new(symbol)
        .map_err(|_| "platform symbol name contained a null byte".to_string())?;
    let local_symbol = unsafe { dlsym(library.0, symbol_name.as_ptr()) } as usize;
    if local_symbol == 0 {
        return Err(format!(
            "platform symbol {} is unavailable",
            String::from_utf8_lossy(symbol)
        ));
    }
    let Some(local_symbol_mapping) = local_mappings.iter().find(|mapping| {
        mapping.module == module
            && mapping.executable
            && mapping.start <= local_symbol
            && local_symbol < mapping.end
    }) else {
        return Err(format!(
            "platform symbol {} resolved outside its library",
            String::from_utf8_lossy(symbol)
        ));
    };
    let local_image = local_symbol_mapping.image;
    let remote_image = unique_process_image(remote_mappings, module, local_symbol_mapping.location)
        .ok_or_else(|| "target platform library location is missing or ambiguous".to_string())?;

    let local_base = process_image_base(local_mappings, module, local_image)
        .ok_or_else(|| "local platform library base is unavailable".to_string())?;
    let remote_base = process_image_base(remote_mappings, module, remote_image)
        .ok_or_else(|| "target platform library base is unavailable".to_string())?;

    if !verified_images
        .iter()
        .any(|pair| *pair == (module, local_image, remote_image))
    {
        verify_matching_elf_identity(
            pid,
            local_mappings,
            remote_mappings,
            module,
            local_image,
            remote_image,
            local_base,
            remote_base,
        )?;
        verified_images.push((module, local_image, remote_image));
    }

    let offset = local_symbol
        .checked_sub(local_base)
        .filter(|value| *value <= MAXIMUM_LIBRARY_OFFSET)
        .ok_or_else(|| "platform symbol offset failed validation".to_string())?;
    let remote_symbol = remote_base
        .checked_add(offset)
        .ok_or_else(|| "target symbol address overflow".to_string())?;
    if !remote_mappings.iter().any(|mapping| {
        mapping.module == module
            && mapping.image == remote_image
            && mapping.executable
            && mapping.start <= remote_symbol
            && remote_symbol < mapping.end
    }) {
        return Err(format!(
            "target symbol {} resolved outside its library",
            String::from_utf8_lossy(symbol)
        ));
    }
    Ok((remote_symbol, remote_image))
}

#[allow(clippy::too_many_arguments)]
fn verify_matching_elf_identity(
    pid: i32,
    local_mappings: &[ProcessMapping],
    remote_mappings: &[ProcessMapping],
    module: ProcessModule,
    local_image: ProcessImageId,
    remote_image: ProcessImageId,
    local_base: usize,
    remote_base: usize,
) -> Result<(), String> {
    let local_identity = read_elf_identity(local_base, |address, output| {
        read_local_image_memory(local_mappings, module, local_image, address, output)
    })
    .map_err(|error| format!("local platform image identity is unavailable: {error}"))?;
    let remote_identity = read_elf_identity(remote_base, |address, output| {
        read_remote_image_memory(pid, remote_mappings, module, remote_image, address, output)
    })
    .map_err(|error| format!("target platform image identity is unavailable: {error}"))?;

    if local_identity != remote_identity {
        return Err(
            "target platform library ELF build identity does not match the injector".into(),
        );
    }
    Ok(())
}

fn read_local_image_memory(
    mappings: &[ProcessMapping],
    module: ProcessModule,
    image: ProcessImageId,
    address: usize,
    output: &mut [u8],
) -> bool {
    if !image_contains_range(mappings, module, image, address, output.len()) {
        return false;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(address as *const u8, output.as_mut_ptr(), output.len());
    }
    true
}

fn read_remote_image_memory(
    pid: i32,
    mappings: &[ProcessMapping],
    module: ProcessModule,
    image: ProcessImageId,
    address: usize,
    output: &mut [u8],
) -> bool {
    image_contains_range(mappings, module, image, address, output.len())
        && read_process_memory(pid, address, output)
}

fn image_contains_range(
    mappings: &[ProcessMapping],
    module: ProcessModule,
    image: ProcessImageId,
    address: usize,
    length: usize,
) -> bool {
    if address == 0 || length == 0 {
        return false;
    }
    let Some(end) = address.checked_add(length) else {
        return false;
    };
    mappings.iter().any(|mapping| {
        mapping.module == module
            && mapping.image == image
            && mapping.start <= address
            && end <= mapping.end
    })
}

fn read_elf_identity(
    base: usize,
    mut read_memory: impl FnMut(usize, &mut [u8]) -> bool,
) -> Result<ElfImageIdentity, String> {
    if base == 0 {
        return Err("zero ELF base".into());
    }
    let mut header = [0u8; ELF_HEADER_SIZE];
    if !read_memory(base, &mut header) {
        return Err("could not read ELF header".into());
    }
    if header[..4] != *b"\x7fELF"
        || header[4] != ELF_CLASS_64
        || header[5] != ELF_DATA_LITTLE_ENDIAN
        || header[6] != ELF_VERSION_CURRENT
    {
        return Err("unsupported ELF identification".into());
    }
    if read_u16(&header, 16) != Some(ELF_TYPE_SHARED_OBJECT)
        || read_u32(&header, 20) != Some(u32::from(ELF_VERSION_CURRENT))
        || read_u16(&header, 52) != Some(ELF_HEADER_SIZE as u16)
    {
        return Err("invalid ELF header".into());
    }
    let machine = read_u16(&header, 18).ok_or_else(|| "missing ELF machine".to_string())?;
    if !matches!(machine, ELF_MACHINE_X86_64 | ELF_MACHINE_AARCH64) {
        return Err("unsupported ELF machine".into());
    }
    let flags = read_u32(&header, 48).ok_or_else(|| "missing ELF flags".to_string())?;
    let program_header_offset = usize::try_from(
        read_u64(&header, 32).ok_or_else(|| "missing ELF program header offset".to_string())?,
    )
    .map_err(|_| "ELF program header offset is too large".to_string())?;
    if !(ELF_HEADER_SIZE..=MAXIMUM_ELF_PROGRAM_HEADER_OFFSET).contains(&program_header_offset) {
        return Err("ELF program header offset is outside its bound".into());
    }
    let program_header_size = usize::from(
        read_u16(&header, 54).ok_or_else(|| "missing ELF program header size".to_string())?,
    );
    if program_header_size != ELF_PROGRAM_HEADER_SIZE {
        return Err("unexpected ELF program header size".into());
    }
    let program_header_count = usize::from(
        read_u16(&header, 56).ok_or_else(|| "missing ELF program header count".to_string())?,
    );
    if program_header_count == 0 || program_header_count > MAXIMUM_ELF_PROGRAM_HEADERS {
        return Err("ELF program header count is outside its bound".into());
    }
    let table_length = program_header_count
        .checked_mul(program_header_size)
        .filter(|length| *length <= MAXIMUM_ELF_PROGRAM_HEADER_TABLE_BYTES)
        .ok_or_else(|| "ELF program header table is too large".to_string())?;
    let table_address = base
        .checked_add(program_header_offset)
        .ok_or_else(|| "ELF program header address overflow".to_string())?;
    let mut table = vec![0u8; table_length];
    if !read_memory(table_address, &mut table) {
        return Err("could not read ELF program headers".into());
    }

    let mut build_id: Option<Vec<u8>> = None;
    for entry in table.chunks_exact(program_header_size) {
        if read_u32(entry, 0) != Some(PROGRAM_HEADER_NOTE) {
            continue;
        }
        let virtual_address = usize::try_from(
            read_u64(entry, 16).ok_or_else(|| "malformed ELF note address".to_string())?,
        )
        .map_err(|_| "ELF note address is too large".to_string())?;
        let file_size = usize::try_from(
            read_u64(entry, 32).ok_or_else(|| "malformed ELF note size".to_string())?,
        )
        .map_err(|_| "ELF note size is too large".to_string())?;
        let memory_size = usize::try_from(
            read_u64(entry, 40).ok_or_else(|| "malformed ELF note memory size".to_string())?,
        )
        .map_err(|_| "ELF note memory size is too large".to_string())?;
        if file_size == 0 {
            continue;
        }
        if file_size > memory_size || file_size > MAXIMUM_ELF_NOTE_BYTES {
            return Err("ELF note segment is outside its bound".into());
        }
        let note_address = base
            .checked_add(virtual_address)
            .ok_or_else(|| "ELF note address overflow".to_string())?;
        let mut notes = vec![0u8; file_size];
        if !read_memory(note_address, &mut notes) {
            return Err("could not read ELF note segment".into());
        }
        if let Some(candidate) = find_gnu_build_id(&notes)? {
            match &build_id {
                None => build_id = Some(candidate),
                Some(existing) if *existing == candidate => {}
                Some(_) => return Err("conflicting GNU build IDs".into()),
            }
        }
    }

    let build_id = build_id.ok_or_else(|| "GNU build ID is missing".to_string())?;
    Ok(ElfImageIdentity {
        machine,
        flags,
        build_id,
    })
}

fn find_gnu_build_id(input: &[u8]) -> Result<Option<Vec<u8>>, String> {
    let mut cursor = 0usize;
    let mut build_id: Option<Vec<u8>> = None;
    while cursor < input.len() {
        if input.len() - cursor < 12 {
            if input[cursor..].iter().all(|byte| *byte == 0) {
                break;
            }
            return Err("truncated ELF note header".into());
        }
        let namesz = usize::try_from(
            read_u32(input, cursor).ok_or_else(|| "malformed ELF note name size".to_string())?,
        )
        .map_err(|_| "ELF note name size is too large".to_string())?;
        let descsz = usize::try_from(
            read_u32(input, cursor + 4)
                .ok_or_else(|| "malformed ELF note descriptor size".to_string())?,
        )
        .map_err(|_| "ELF note descriptor size is too large".to_string())?;
        let note_type =
            read_u32(input, cursor + 8).ok_or_else(|| "malformed ELF note type".to_string())?;
        cursor += 12;

        let name_end = cursor
            .checked_add(namesz)
            .filter(|end| *end <= input.len())
            .ok_or_else(|| "ELF note name exceeds segment".to_string())?;
        let name = &input[cursor..name_end];
        cursor = align_four(name_end).ok_or_else(|| "ELF note name overflow".to_string())?;
        if cursor > input.len() {
            return Err("ELF note name padding exceeds segment".into());
        }

        let descriptor_end = cursor
            .checked_add(descsz)
            .filter(|end| *end <= input.len())
            .ok_or_else(|| "ELF note descriptor exceeds segment".to_string())?;
        let descriptor = &input[cursor..descriptor_end];
        cursor =
            align_four(descriptor_end).ok_or_else(|| "ELF note descriptor overflow".to_string())?;
        if cursor > input.len() {
            return Err("ELF note descriptor padding exceeds segment".into());
        }

        if note_type == GNU_BUILD_ID_NOTE && name == GNU_NOTE_NAME {
            if descriptor.is_empty() || descriptor.len() > MAXIMUM_BUILD_ID_BYTES {
                return Err("GNU build ID length is outside its bound".into());
            }
            match &build_id {
                None => build_id = Some(descriptor.to_vec()),
                Some(existing) if existing.as_slice() == descriptor => {}
                Some(_) => return Err("conflicting GNU build IDs in note segment".into()),
            }
        }
    }
    Ok(build_id)
}

fn align_four(value: usize) -> Option<usize> {
    value.checked_add(3).map(|value| value & !3)
}

fn read_u16(input: &[u8], offset: usize) -> Option<u16> {
    let bytes: [u8; 2] = input.get(offset..offset.checked_add(2)?)?.try_into().ok()?;
    Some(u16::from_le_bytes(bytes))
}

fn read_u32(input: &[u8], offset: usize) -> Option<u32> {
    let bytes: [u8; 4] = input.get(offset..offset.checked_add(4)?)?.try_into().ok()?;
    Some(u32::from_le_bytes(bytes))
}

fn read_u64(input: &[u8], offset: usize) -> Option<u64> {
    let bytes: [u8; 8] = input.get(offset..offset.checked_add(8)?)?.try_into().ok()?;
    Some(u64::from_le_bytes(bytes))
}

#[cfg(test)]
mod tests {
    use super::*;

    const TEST_BASE: usize = 0x1000;

    fn write_u16(output: &mut [u8], offset: usize, value: u16) {
        output[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
    }

    fn write_u32(output: &mut [u8], offset: usize, value: u32) {
        output[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
    }

    fn write_u64(output: &mut [u8], offset: usize, value: u64) {
        output[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
    }

    fn synthetic_elf(machine: u16, flags: u32, build_id: &[u8]) -> Vec<u8> {
        let note_offset = 0x100usize;
        let mut image = vec![0u8; 0x200];
        image[..4].copy_from_slice(b"\x7fELF");
        image[4] = ELF_CLASS_64;
        image[5] = ELF_DATA_LITTLE_ENDIAN;
        image[6] = ELF_VERSION_CURRENT;
        write_u16(&mut image, 16, ELF_TYPE_SHARED_OBJECT);
        write_u16(&mut image, 18, machine);
        write_u32(&mut image, 20, u32::from(ELF_VERSION_CURRENT));
        write_u64(&mut image, 32, ELF_HEADER_SIZE as u64);
        write_u32(&mut image, 48, flags);
        write_u16(&mut image, 52, ELF_HEADER_SIZE as u16);
        write_u16(&mut image, 54, ELF_PROGRAM_HEADER_SIZE as u16);
        write_u16(&mut image, 56, 1);

        write_u32(&mut image, ELF_HEADER_SIZE, PROGRAM_HEADER_NOTE);
        write_u64(&mut image, ELF_HEADER_SIZE + 16, note_offset as u64);
        let descriptor_offset = note_offset + 16;
        let note_length = align_four(descriptor_offset + build_id.len()).unwrap() - note_offset;
        write_u64(&mut image, ELF_HEADER_SIZE + 32, note_length as u64);
        write_u64(&mut image, ELF_HEADER_SIZE + 40, note_length as u64);

        write_u32(&mut image, note_offset, GNU_NOTE_NAME.len() as u32);
        write_u32(&mut image, note_offset + 4, build_id.len() as u32);
        write_u32(&mut image, note_offset + 8, GNU_BUILD_ID_NOTE);
        image[note_offset + 12..note_offset + 16].copy_from_slice(GNU_NOTE_NAME);
        image[descriptor_offset..descriptor_offset + build_id.len()].copy_from_slice(build_id);
        image
    }

    fn identity_from_image(image: &[u8]) -> Result<ElfImageIdentity, String> {
        read_elf_identity(TEST_BASE, |address, output| {
            let Some(offset) = address.checked_sub(TEST_BASE) else {
                return false;
            };
            let Some(end) = offset.checked_add(output.len()) else {
                return false;
            };
            let Some(source) = image.get(offset..end) else {
                return false;
            };
            output.copy_from_slice(source);
            true
        })
    }

    #[test]
    fn reads_bounded_gnu_build_identity() {
        let image = synthetic_elf(ELF_MACHINE_AARCH64, 0, &[1, 2, 3, 4, 5, 6, 7, 8]);
        let identity = identity_from_image(&image).unwrap();
        assert_eq!(identity.machine, ELF_MACHINE_AARCH64);
        assert_eq!(identity.flags, 0);
        assert_eq!(identity.build_id, vec![1, 2, 3, 4, 5, 6, 7, 8]);
    }

    #[test]
    fn different_builds_or_abis_do_not_share_an_identity() {
        let first =
            identity_from_image(&synthetic_elf(ELF_MACHINE_AARCH64, 0, &[1, 2, 3, 4])).unwrap();
        let different_build =
            identity_from_image(&synthetic_elf(ELF_MACHINE_AARCH64, 0, &[1, 2, 3, 5])).unwrap();
        let different_abi =
            identity_from_image(&synthetic_elf(ELF_MACHINE_X86_64, 0, &[1, 2, 3, 4])).unwrap();
        assert_ne!(first, different_build);
        assert_ne!(first, different_abi);
    }

    #[test]
    fn rejects_missing_or_oversized_build_identity() {
        let missing = synthetic_elf(ELF_MACHINE_AARCH64, 0, &[]);
        assert!(identity_from_image(&missing).is_err());

        let oversized = synthetic_elf(
            ELF_MACHINE_AARCH64,
            0,
            &vec![0x42; MAXIMUM_BUILD_ID_BYTES + 1],
        );
        assert!(identity_from_image(&oversized).is_err());
    }
}
