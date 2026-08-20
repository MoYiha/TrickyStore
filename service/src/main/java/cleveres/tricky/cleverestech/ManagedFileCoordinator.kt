package cleveres.tricky.cleverestech

/**
 * Process-wide serialization boundary for mutations initiated by the managed WebUI and
 * background maintenance jobs.
 *
 * Reads may still use immutable descriptor-backed snapshots without this monitor. A workflow
 * that validates a snapshot and then mutates its path must hold [monitor] from the final identity
 * check through the mutation and matching runtime refresh.
 */
internal object ManagedFileCoordinator {
    val monitor = Any()
}
