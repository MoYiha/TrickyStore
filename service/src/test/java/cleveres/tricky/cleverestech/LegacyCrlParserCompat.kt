package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.KeyboxVerifier
import cleveres.tricky.cleverestech.util.ManagedCrlOracle
import java.io.Reader

/** Frozen test-only compatibility surface for legacy CRL parser characterization. */
internal fun KeyboxVerifier.parseCrl(reader: Reader): Set<String> = ManagedCrlOracle.parse(reader)
