#ifndef __BPF_HELPERS_H
#define __BPF_HELPERS_H

/* helper macro to place programs, maps, license in
 * different sections in elf_bpf file. Section names
 * are interpreted by elf_bpf loader
 */
#define SEC(NAME) __attribute__((section(NAME), used))

/* helper functions called from eBPF programs support mapping to
 * BPF constants, e.g.
 * const int BPF_FUNC_map_lookup_elem = 1;
 * const int BPF_FUNC_map_update_elem = 2;
 * const int BPF_FUNC_map_delete_elem = 3;
 * ...
 */

static void *(*bpf_map_lookup_elem)(void *map, void *key) =
    (void *) 1;
static int (*bpf_map_update_elem)(void *map, void *key, void *value,
                                  unsigned long long flags) =
    (void *) 2;
static int (*bpf_map_delete_elem)(void *map, void *key) =
    (void *) 3;
static int (*bpf_probe_read)(void *dst, int size, void *src) =
    (void *) 4;
static unsigned long long (*bpf_ktime_get_ns)(void) =
    (void *) 5;
static int (*bpf_trace_printk)(const char *fmt, int fmt_size, ...) =
    (void *) 6;
static unsigned long long (*bpf_get_prandom_u32)(void) =
    (void *) 7;
static unsigned long long (*bpf_get_smp_processor_id)(void) =
    (void *) 8;
static unsigned long long (*bpf_get_current_pid_tgid)(void) =
    (void *) 14;
static unsigned long long (*bpf_get_current_uid_gid)(void) =
    (void *) 15;
static int (*bpf_get_current_comm)(void *buf, int buf_size) =
    (void *) 16;
static int (*bpf_perf_event_read)(void *map, unsigned long long flags) =
    (void *) 22;
static int (*bpf_clone_redirect)(void *skb, int ifindex, int flags) =
    (void *) 13;
static int (*bpf_redirect)(int ifindex, int flags) =
    (void *) 23;

/* llvm built-in functions */
unsigned long long load_byte(void *skb,
                             unsigned long long off) asm("llvm.bpf.load.byte");
unsigned long long load_half(void *skb,
                             unsigned long long off) asm("llvm.bpf.load.half");
unsigned long long load_word(void *skb,
                             unsigned long long off) asm("llvm.bpf.load.word");

#endif
