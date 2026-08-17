package cleveres.tricky.cleverestech.util

import java.io.Reader

/** Frozen test-only compatibility surface for legacy CRL parser characterization. */
internal fun KeyboxVerifier.parseCrl(reader: Reader): Set<String> = ManagedCrlOracle.parse(reader)

/** Preserves the legacy test call shape without restoring a production String parser. */
internal fun KeyboxVerifier.parseCrl(xml: String): Set<String> = parseCrl(xml.reader())
