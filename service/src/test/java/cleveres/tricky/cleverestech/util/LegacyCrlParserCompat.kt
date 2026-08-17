package cleveres.tricky.cleverestech.util

import java.io.Reader

/** Frozen test-only compatibility surface for legacy CRL parser characterization. */
internal fun KeyboxVerifier.parseCrl(reader: Reader): Set<String> = ManagedCrlOracle.parse(reader)
