use crate::abi::getpid;
#[cfg(target_arch = "aarch64")]
use crate::abi::IoVector;
use crate::logging;
use crate::process_memory::{read_process_memory, write_process_memory};
use cleverestricky_native_core::injector_support::{
    is_synthetic_return_trap, remote_stop_signal_to_deliver, sanitize_signal_for_detach,
    validate_attached_target_cmdline, wipe_bytes,
};
use std::ffi::{c_int, c_void, CStr};
use std::mem;

const LOG_ERROR: c_int = 6;
const PTRACE_CONT: c_int = 7;
#[cfg(target_arch = "x86_64")]
const PTRACE_GETREGS: c_int = 12;
#[cfg(target_arch = "x86_64")]
const PTRACE_SETREGS: c_int = 13;
const PTRACE_ATTACH: c_int = 16;
const PTRACE_DETACH: c_int = 17;
const PTRACE_GETSIGINFO: c_int = 0x4202;
#[cfg(target_arch = "aarch64")]
const PTRACE_GETREGSET: c_int = 0x4204;
#[cfg(target_arch = "aarch64")]
const PTRACE_SETREGSET: c_int = 0x4205;
#[cfg(target_arch = "aarch64")]
const NT_PRSTATUS: usize = 1;
const WAIT_ALL: c_int = 0x4000_0000;
const INTERRUPTED_SYSTEM_CALL: i32 = 4;
const SIGNAL_STOP: i32 = 19;
const MAXIMUM_ARGUMENTS: usize = 32;
const MAXIMUM_REMOTE_INPUT: usize = 64 * 1_024;
const MAXIMUM_SAVED_STACK_BYTES: usize = 256 * 1_024;
const REMOTE_CALL_STACK_GUARD_BYTES: usize = 16 * 1_024;
const STACK_ALIGNMENT: usize = 16;

#[derive(Clone, Copy, Debug, Default)]
#[repr(C, align(8))]
struct SignalInfo {
    // Supported Android LP64 architectures use the generic 128-byte siginfo
    // layout: signo, errno, and si_code are the first three 32-bit words.
    words: [i32; 32],
}

const _: [(); 128] = [(); mem::size_of::<SignalInfo>()];
const _: [(); 8] = [(); mem::align_of::<SignalInfo>()];

extern "C" {
    fn ptrace(request: c_int, pid: c_int, address: *mut c_void, data: *mut c_void) -> isize;
    fn waitpid(pid: c_int, status: *mut c_int, options: c_int) -> c_int;
}

#[cfg(target_arch = "x86_64")]
#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
struct Registers {
    r15: u64,
    r14: u64,
    r13: u64,
    r12: u64,
    rbp: u64,
    rbx: u64,
    r11: u64,
    r10: u64,
    r9: u64,
    r8: u64,
    rax: u64,
    rcx: u64,
    rdx: u64,
    rsi: u64,
    rdi: u64,
    orig_rax: u64,
    rip: u64,
    cs: u64,
    eflags: u64,
    rsp: u64,
    ss: u64,
    fs_base: u64,
    gs_base: u64,
    ds: u64,
    es: u64,
    fs: u64,
    gs: u64,
}

#[cfg(target_arch = "x86_64")]
const _: [(); 216] = [(); mem::size_of::<Registers>()];
#[cfg(target_arch = "x86_64")]
const _: [(); 80] = [(); mem::offset_of!(Registers, rax)];
#[cfg(target_arch = "x86_64")]
const _: [(); 128] = [(); mem::offset_of!(Registers, rip)];
#[cfg(target_arch = "x86_64")]
const _: [(); 152] = [(); mem::offset_of!(Registers, rsp)];

#[cfg(target_arch = "aarch64")]
#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
struct Registers {
    values: [u64; 31],
    stack_pointer: u64,
    program_counter: u64,
    processor_state: u64,
}

#[cfg(target_arch = "aarch64")]
const _: [(); 272] = [(); mem::size_of::<Registers>()];
#[cfg(target_arch = "aarch64")]
const _: [(); 248] = [(); mem::offset_of!(Registers, stack_pointer)];
#[cfg(target_arch = "aarch64")]
const _: [(); 256] = [(); mem::offset_of!(Registers, program_counter)];

#[cfg(not(any(target_arch = "aarch64", target_arch = "x86_64")))]
compile_error!("the injector supports only arm64 and x86 64");

pub(crate) struct RemoteSession {
    pid: i32,
    registers: Registers,
    original_registers: Registers,
    original_registers_valid: bool,
    attached: bool,
    pending_signal: i32,
    remote_calls_blocked: bool,
    stack_patches: Vec<StackPatch>,
    saved_stack_bytes: usize,
    preserved_stack_floor: usize,
}

struct StackPatch {
    address: usize,
    original: Vec<u8>,
}

impl Drop for StackPatch {
    fn drop(&mut self) {
        wipe_bytes(&mut self.original);
    }
}

impl RemoteSession {
    pub(crate) fn attach(pid: i32) -> Result<Self, String> {
        if pid <= 0 || pid == unsafe { getpid() } {
            return Err("invalid target process".into());
        }
        if unsafe {
            ptrace(
                PTRACE_ATTACH,
                pid,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
            )
        } == -1
        {
            return Err(format!("could not attach to process {pid}"));
        }

        let mut session = Self {
            pid,
            registers: Registers::default(),
            original_registers: Registers::default(),
            original_registers_valid: false,
            attached: true,
            pending_signal: 0,
            remote_calls_blocked: false,
            stack_patches: Vec::with_capacity(16),
            saved_stack_bytes: 0,
            preserved_stack_floor: usize::MAX,
        };
        loop {
            let status = wait_for_stop(pid)?;
            let signal = stop_signal(status);
            if signal == SIGNAL_STOP {
                break;
            }
            let signal = sanitize_signal_for_detach(signal);
            if signal == 0
                || unsafe {
                    ptrace(
                        PTRACE_CONT,
                        pid,
                        std::ptr::null_mut(),
                        signal as usize as *mut c_void,
                    )
                } == -1
            {
                return Err(format!(
                    "could not preserve a signal delivered during attach: {status:#x}"
                ));
            }
        }
        if !validate_attached_target_cmdline(pid).unwrap_or(false) {
            return Err("target process identity validation failed".into());
        }
        session.registers = read_registers(pid)?;
        session.original_registers = session.registers;
        session.original_registers_valid = true;
        session.preserved_stack_floor = stack_pointer(&session.original_registers);
        Ok(session)
    }

    pub(crate) fn call(
        &mut self,
        function_address: usize,
        return_address: usize,
        arguments: &[usize],
    ) -> Result<usize, String> {
        if !self.attached
            || self.remote_calls_blocked
            || self.pending_signal != 0
            || function_address == 0
            || arguments.len() > MAXIMUM_ARGUMENTS
        {
            return Err("invalid remote call plan".into());
        }
        let base_registers = self.registers;
        let mut call_registers = base_registers;
        let current_stack = stack_pointer(&call_registers);
        let call_stack = call_stack_start(&call_registers, arguments)?;
        if call_stack < current_stack {
            self.save_stack_range(call_stack, current_stack - call_stack)?;
        }
        prepare_call(
            self.pid,
            &mut call_registers,
            function_address,
            return_address,
            arguments,
        )?;
        write_registers(self.pid, &call_registers)?;
        self.registers = call_registers;
        // Preparation failures happen while the tracee is safely stopped and
        // must not disable cleanup calls. Block further remote execution only
        // for the interval after we attempt to resume this injected call; an
        // unexpected stop or wait failure then remains fail-closed.
        self.remote_calls_blocked = true;
        if unsafe {
            ptrace(
                PTRACE_CONT,
                self.pid,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
            )
        } == -1
        {
            // PTRACE_CONT failed, so the tracee never left the controlled
            // stop and best-effort cleanup remains safe.
            self.remote_calls_blocked = false;
            return Err("could not continue the target process".into());
        }

        let status = wait_for_stop(self.pid)?;
        let signal = stop_signal(status);
        // Preserve a genuine signal if register inspection or remote return
        // validation fails. Only the deliberate non-executable return trap is
        // suppressed when detaching.
        let signal_code = read_signal_code(self.pid, signal);
        self.pending_signal = remote_stop_signal_to_deliver(signal, signal_code, 0, 0);
        let stopped_registers = read_registers(self.pid)?;
        let actual_address = instruction_pointer(&stopped_registers);
        self.pending_signal =
            remote_stop_signal_to_deliver(signal, signal_code, actual_address, return_address);
        if !is_synthetic_return_trap(signal, signal_code, actual_address, return_address) {
            self.registers = stopped_registers;
            return Err(format!(
                "remote call stopped unexpectedly with status {status:#x}"
            ));
        }
        let result = return_value(&stopped_registers);
        self.pending_signal = 0;
        self.remote_calls_blocked = false;
        self.registers = base_registers;
        Ok(result)
    }

    pub(crate) fn push_bytes(&mut self, input: &[u8]) -> Result<usize, String> {
        if !self.attached || input.is_empty() || input.len() > MAXIMUM_REMOTE_INPUT {
            return Err("invalid remote input size".into());
        }
        let original_stack = stack_pointer(&self.registers);
        let lowered_stack = original_stack
            .checked_sub(input.len())
            .ok_or_else(|| "remote stack address underflow".to_string())?;
        let remote_address = align_stack(lowered_stack);
        if remote_address == 0 {
            return Err("invalid remote stack address".into());
        }
        self.save_stack_range(remote_address, input.len())?;
        set_stack_pointer(&mut self.registers, remote_address);
        if !write_process_memory(self.pid, remote_address, input) {
            set_stack_pointer(&mut self.registers, original_stack);
            return Err("could not copy data to the stopped process".into());
        }
        Ok(remote_address)
    }

    pub(crate) fn push_value<T: Copy>(&mut self, value: &T) -> Result<usize, String> {
        let bytes = unsafe {
            std::slice::from_raw_parts((value as *const T).cast::<u8>(), mem::size_of::<T>())
        };
        self.push_bytes(bytes)
    }

    pub(crate) fn push_c_string(&mut self, value: &CStr) -> Result<usize, String> {
        self.push_bytes(value.to_bytes_with_nul())
    }

    pub(crate) fn read_bytes(&mut self, address: usize, output: &mut [u8]) -> Result<(), String> {
        if !self.attached
            || address == 0
            || output.is_empty()
            || output.len() > MAXIMUM_REMOTE_INPUT
        {
            return Err("invalid remote read plan".into());
        }
        if read_process_memory(self.pid, address, output) {
            Ok(())
        } else {
            Err("could not read data from the stopped process".into())
        }
    }

    pub(crate) fn read_value<T: Copy + Default>(&mut self, address: usize) -> Result<T, String> {
        let mut output = T::default();
        let bytes = unsafe {
            std::slice::from_raw_parts_mut(
                (&mut output as *mut T).cast::<u8>(),
                mem::size_of::<T>(),
            )
        };
        self.read_bytes(address, bytes)?;
        Ok(output)
    }

    pub(crate) fn finish(mut self) -> Result<(), String> {
        self.restore_and_detach()
    }

    fn save_stack_range(&mut self, address: usize, length: usize) -> Result<(), String> {
        let range_end = address
            .checked_add(length)
            .ok_or_else(|| "remote stack backup address overflow".to_string())?;
        let original_stack = stack_pointer(&self.original_registers);
        if address == 0
            || length == 0
            || !self.original_registers_valid
            || range_end > original_stack
            || self.preserved_stack_floor > original_stack
        {
            return Err("invalid remote stack backup range".into());
        }
        if address >= self.preserved_stack_floor {
            return Ok(());
        }
        let backup_length = self
            .preserved_stack_floor
            .checked_sub(address)
            .ok_or_else(|| "remote stack backup range underflow".to_string())?;
        let new_total = self
            .saved_stack_bytes
            .checked_add(backup_length)
            .filter(|value| *value <= MAXIMUM_SAVED_STACK_BYTES)
            .ok_or_else(|| "remote stack backup exceeded its bound".to_string())?;
        let mut original = vec![0u8; backup_length];
        if !read_process_memory(self.pid, address, &mut original) {
            wipe_bytes(&mut original);
            return Err("could not preserve target stack memory".into());
        }
        self.stack_patches.push(StackPatch { address, original });
        self.saved_stack_bytes = new_total;
        self.preserved_stack_floor = address;
        Ok(())
    }

    fn restore_and_detach(&mut self) -> Result<(), String> {
        if !self.attached {
            return Ok(());
        }
        let mut restore_error: Option<String> = None;
        for patch in self.stack_patches.iter().rev() {
            if !write_process_memory(self.pid, patch.address, &patch.original) {
                restore_error = Some("could not restore target stack memory".into());
            }
        }
        self.stack_patches.clear();
        self.saved_stack_bytes = 0;
        if self.original_registers_valid {
            self.preserved_stack_floor = stack_pointer(&self.original_registers);
        }
        if self.original_registers_valid
            && write_registers(self.pid, &self.original_registers).is_err()
        {
            restore_error = Some("could not restore target registers".into());
        }
        if unsafe {
            ptrace(
                PTRACE_DETACH,
                self.pid,
                std::ptr::null_mut(),
                self.pending_signal as usize as *mut c_void,
            )
        } == -1
        {
            return Err("could not detach from the target process".into());
        }
        self.attached = false;
        self.pending_signal = 0;
        self.remote_calls_blocked = false;
        restore_error.map_or(Ok(()), Err)
    }
}

impl Drop for RemoteSession {
    fn drop(&mut self) {
        if let Err(error) = self.restore_and_detach() {
            log_error(format!(
                "could not safely restore traced process {}: {error}",
                self.pid
            ));
        }
    }
}

fn wait_for_stop(pid: i32) -> Result<i32, String> {
    loop {
        let mut status = 0;
        let result = unsafe { waitpid(pid, &mut status, WAIT_ALL) };
        if result == pid && is_stopped(status) {
            return Ok(status);
        }
        if result == -1
            && std::io::Error::last_os_error().raw_os_error() == Some(INTERRUPTED_SYSTEM_CALL)
        {
            continue;
        }
        return Err(format!(
            "target process did not reach a controlled stop: {status:#x}"
        ));
    }
}

fn read_signal_code(pid: i32, expected_signal: i32) -> Option<i32> {
    let mut information = SignalInfo::default();
    if unsafe {
        ptrace(
            PTRACE_GETSIGINFO,
            pid,
            std::ptr::null_mut(),
            (&mut information as *mut SignalInfo).cast(),
        )
    } == -1
        || information.words[0] != expected_signal
    {
        return None;
    }
    Some(information.words[2])
}

#[cfg(target_arch = "x86_64")]
fn call_stack_start(registers: &Registers, arguments: &[usize]) -> Result<usize, String> {
    let mut stack = align_stack(stack_pointer(registers));
    if arguments.len() > 6 {
        let stack_bytes = arguments[6..]
            .len()
            .checked_mul(mem::size_of::<usize>())
            .ok_or_else(|| "remote argument size overflow".to_string())?;
        stack = align_stack(
            stack
                .checked_sub(stack_bytes)
                .ok_or_else(|| "remote argument stack underflow".to_string())?,
        );
    }
    let return_stack = stack
        .checked_sub(mem::size_of::<usize>())
        .ok_or_else(|| "remote return stack underflow".to_string())?;
    return_stack
        .checked_sub(REMOTE_CALL_STACK_GUARD_BYTES)
        .ok_or_else(|| "remote call stack guard underflow".to_string())
}

#[cfg(target_arch = "aarch64")]
fn call_stack_start(registers: &Registers, arguments: &[usize]) -> Result<usize, String> {
    let mut stack = align_stack(stack_pointer(registers));
    if arguments.len() > 8 {
        let stack_bytes = arguments[8..]
            .len()
            .checked_mul(mem::size_of::<usize>())
            .ok_or_else(|| "remote argument size overflow".to_string())?;
        stack = align_stack(
            stack
                .checked_sub(stack_bytes)
                .ok_or_else(|| "remote argument stack underflow".to_string())?,
        );
    }
    stack
        .checked_sub(REMOTE_CALL_STACK_GUARD_BYTES)
        .ok_or_else(|| "remote call stack guard underflow".to_string())
}

#[cfg(target_arch = "x86_64")]
fn read_registers(pid: i32) -> Result<Registers, String> {
    let mut registers = Registers::default();
    if unsafe {
        ptrace(
            PTRACE_GETREGS,
            pid,
            std::ptr::null_mut(),
            (&mut registers as *mut Registers).cast(),
        )
    } == -1
    {
        Err("could not read x86 64 target registers".into())
    } else {
        Ok(registers)
    }
}

#[cfg(target_arch = "x86_64")]
fn write_registers(pid: i32, registers: &Registers) -> Result<(), String> {
    if unsafe {
        ptrace(
            PTRACE_SETREGS,
            pid,
            std::ptr::null_mut(),
            (registers as *const Registers).cast_mut().cast(),
        )
    } == -1
    {
        Err("could not write x86 64 target registers".into())
    } else {
        Ok(())
    }
}

#[cfg(target_arch = "aarch64")]
fn read_registers(pid: i32) -> Result<Registers, String> {
    let mut registers = Registers::default();
    let mut vector = IoVector {
        base: (&mut registers as *mut Registers).cast(),
        length: mem::size_of::<Registers>(),
    };
    if unsafe {
        ptrace(
            PTRACE_GETREGSET,
            pid,
            NT_PRSTATUS as *mut c_void,
            (&mut vector as *mut IoVector).cast(),
        )
    } == -1
        || vector.length != mem::size_of::<Registers>()
    {
        Err("could not read ARM64 target registers".into())
    } else {
        Ok(registers)
    }
}

#[cfg(target_arch = "aarch64")]
fn write_registers(pid: i32, registers: &Registers) -> Result<(), String> {
    let mut vector = IoVector {
        base: (registers as *const Registers).cast_mut().cast(),
        length: mem::size_of::<Registers>(),
    };
    if unsafe {
        ptrace(
            PTRACE_SETREGSET,
            pid,
            NT_PRSTATUS as *mut c_void,
            (&mut vector as *mut IoVector).cast(),
        )
    } == -1
    {
        Err("could not write ARM64 target registers".into())
    } else {
        Ok(())
    }
}

#[cfg(target_arch = "x86_64")]
fn prepare_call(
    pid: i32,
    registers: &mut Registers,
    function_address: usize,
    return_address: usize,
    arguments: &[usize],
) -> Result<(), String> {
    let mut stack = align_stack(stack_pointer(registers));
    if let Some(value) = arguments.first() {
        registers.rdi = *value as u64;
    }
    if let Some(value) = arguments.get(1) {
        registers.rsi = *value as u64;
    }
    if let Some(value) = arguments.get(2) {
        registers.rdx = *value as u64;
    }
    if let Some(value) = arguments.get(3) {
        registers.rcx = *value as u64;
    }
    if let Some(value) = arguments.get(4) {
        registers.r8 = *value as u64;
    }
    if let Some(value) = arguments.get(5) {
        registers.r9 = *value as u64;
    }
    if arguments.len() > 6 {
        let stack_arguments = &arguments[6..];
        let stack_bytes = stack_arguments
            .len()
            .checked_mul(mem::size_of::<usize>())
            .ok_or_else(|| "remote argument size overflow".to_string())?;
        stack = align_stack(
            stack
                .checked_sub(stack_bytes)
                .ok_or_else(|| "remote argument stack underflow".to_string())?,
        );
        if !write_words(pid, stack, stack_arguments) {
            return Err("could not write remote stack arguments".into());
        }
    }
    stack = stack
        .checked_sub(mem::size_of::<usize>())
        .ok_or_else(|| "remote return stack underflow".to_string())?;
    if !write_words(pid, stack, std::slice::from_ref(&return_address)) {
        return Err("could not write the remote return address".into());
    }
    registers.rsp = stack as u64;
    registers.rip = function_address as u64;
    Ok(())
}

#[cfg(target_arch = "aarch64")]
fn prepare_call(
    pid: i32,
    registers: &mut Registers,
    function_address: usize,
    return_address: usize,
    arguments: &[usize],
) -> Result<(), String> {
    let mut stack = align_stack(stack_pointer(registers));
    for (index, value) in arguments.iter().take(8).enumerate() {
        registers.values[index] = *value as u64;
    }
    if arguments.len() > 8 {
        let stack_arguments = &arguments[8..];
        let stack_bytes = stack_arguments
            .len()
            .checked_mul(mem::size_of::<usize>())
            .ok_or_else(|| "remote argument size overflow".to_string())?;
        stack = align_stack(
            stack
                .checked_sub(stack_bytes)
                .ok_or_else(|| "remote argument stack underflow".to_string())?,
        );
        if !write_words(pid, stack, stack_arguments) {
            return Err("could not write remote stack arguments".into());
        }
    }
    registers.values[30] = return_address as u64;
    registers.stack_pointer = stack as u64;
    registers.program_counter = function_address as u64;
    Ok(())
}

fn write_words(pid: i32, remote_address: usize, values: &[usize]) -> bool {
    let bytes = unsafe {
        std::slice::from_raw_parts(values.as_ptr().cast::<u8>(), mem::size_of_val(values))
    };
    !bytes.is_empty() && write_process_memory(pid, remote_address, bytes)
}

const fn align_stack(address: usize) -> usize {
    address & !(STACK_ALIGNMENT - 1)
}

const fn is_stopped(status: i32) -> bool {
    status & 0xff == 0x7f
}

const fn stop_signal(status: i32) -> i32 {
    (status >> 8) & 0xff
}

#[cfg(target_arch = "x86_64")]
fn stack_pointer(registers: &Registers) -> usize {
    registers.rsp as usize
}

#[cfg(target_arch = "aarch64")]
fn stack_pointer(registers: &Registers) -> usize {
    registers.stack_pointer as usize
}

#[cfg(target_arch = "x86_64")]
fn set_stack_pointer(registers: &mut Registers, value: usize) {
    registers.rsp = value as u64;
}

#[cfg(target_arch = "aarch64")]
fn set_stack_pointer(registers: &mut Registers, value: usize) {
    registers.stack_pointer = value as u64;
}

#[cfg(target_arch = "x86_64")]
fn instruction_pointer(registers: &Registers) -> usize {
    registers.rip as usize
}

#[cfg(target_arch = "aarch64")]
fn instruction_pointer(registers: &Registers) -> usize {
    registers.program_counter as usize
}

#[cfg(target_arch = "x86_64")]
fn return_value(registers: &Registers) -> usize {
    registers.rax as usize
}

#[cfg(target_arch = "aarch64")]
fn return_value(registers: &Registers) -> usize {
    registers.values[0] as usize
}

fn log_error(message: String) {
    logging::write(LOG_ERROR, message);
}
