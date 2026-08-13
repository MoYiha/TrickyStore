use crate::abi::InjectorSymbols;
use cleverestricky_native_core::injector_support::{
    process_image_base, read_relevant_process_maps, unique_process_image, ProcessImageId,
    ProcessMapping, ProcessModule,
};
use std::ffi::{c_char, c_int, c_void, CString};

const RTLD_NOW: c_int = 2;
const MAXIMUM_LIBRARY_OFFSET: usize = 512 * 1_024 * 1_024;

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

    let (close_address, libc_image) =
        resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"close")?;
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

    output.socket = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"socket")?.0;
    output.bind = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"bind")?.0;
    output.recvmsg = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"recvmsg")?.0;
    output.mmap = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"mmap")?.0;
    output.munmap = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"munmap")?.0;
    output.errno_location = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"__errno")
        .map(|resolved| resolved.0)
        .unwrap_or(0);
    output.android_dlopen_ext = resolve_symbol(
        &local,
        &remote,
        ProcessModule::Libdl,
        &libdl,
        b"android_dlopen_ext",
    )?
    .0;
    output.dlerror = resolve_symbol(&local, &remote, ProcessModule::Libdl, &libdl, b"dlerror")
        .map(|resolved| resolved.0)
        .unwrap_or(0);
    output.strlen = resolve_symbol(&local, &remote, ProcessModule::Libc, &libc, b"strlen")
        .map(|resolved| resolved.0)
        .unwrap_or(0);
    output.dlsym = resolve_symbol(&local, &remote, ProcessModule::Libdl, &libdl, b"dlsym")?.0;
    output.dlclose = resolve_symbol(&local, &remote, ProcessModule::Libdl, &libdl, b"dlclose")?.0;
    Ok(output)
}

fn resolve_symbol(
    local_mappings: &[ProcessMapping],
    remote_mappings: &[ProcessMapping],
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
