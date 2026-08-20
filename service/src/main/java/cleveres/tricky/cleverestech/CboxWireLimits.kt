package cleveres.tricky.cleverestech

/** Managed mirror of the bounded CBOX envelope contract implemented by the Rust crypto cores. */
internal object CboxWireLimits {
    const val HEADER_BYTES = 4 + 4 + 16 + 12
    const val TAG_BYTES = 16
    const val MAX_CIPHERTEXT_BYTES = 10 * 1024 * 1024
    const val MIN_BYTES = HEADER_BYTES + TAG_BYTES
    const val MAX_BYTES = HEADER_BYTES + MAX_CIPHERTEXT_BYTES
}
