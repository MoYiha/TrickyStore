package com.android.keystore

import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedList

class QuotaManager {
    // HARDWARE QUOTA SIMULATOR
    // Limit concurrent open operations

    private val teeOperations = ConcurrentHashMap<Int, LinkedList<Int>>()
    private val strongBoxOperations = ConcurrentHashMap<Int, LinkedList<Int>>()
    private val evictedOperations = ConcurrentHashMap<Int, MutableSet<Int>>()

    // Rate Limiting sliding window
    private val strongBoxCreateTimes = ConcurrentHashMap<Int, LinkedList<Long>>()

    companion object {
        const val MAX_TEE_OPS = 15
        const val MAX_STRONGBOX_OPS = 4

        const val INVALID_OPERATION_HANDLE = -28
        const val TOO_MANY_OPERATIONS = -29
        const val TOO_MUCH_DATA = -21 // Simplified for > 32KB
        const val HARDWARE_FALLBACK_REQUIRED = -100
    }

    fun openOperation(uid: Int, opHandle: Int, isStrongBox: Boolean): Int {
        val opsMap = if (isStrongBox) strongBoxOperations else teeOperations
        val maxOps = if (isStrongBox) MAX_STRONGBOX_OPS else MAX_TEE_OPS

        val ops = opsMap.computeIfAbsent(uid) { LinkedList() }

        synchronized(ops) {
            if (ops.size >= maxOps) {
                // LRU Eviction: Evict the oldest operation
                val evicted = ops.removeFirst()
                evictedOperations.computeIfAbsent(uid) { mutableSetOf() }.add(evicted)
            }
            ops.addLast(opHandle)
        }

        return 0 // Success
    }

    fun accessOperation(uid: Int, opHandle: Int): Int {
        if (evictedOperations[uid]?.contains(opHandle) == true) {
            return INVALID_OPERATION_HANDLE
        }
        return 0
    }

    fun createStrongBoxOperation(uid: Int): Int {
        val times = strongBoxCreateTimes.computeIfAbsent(uid) { LinkedList() }
        val now = System.currentTimeMillis()

        synchronized(times) {
            // Remove times older than 1 second (sliding window)
            while (times.isNotEmpty() && now - times.first > 1000) {
                times.removeFirst()
            }

            if (times.size >= 10) { // Limit to 10 ops per second
                return TOO_MANY_OPERATIONS
            }

            times.addLast(now)
        }

        return 0
    }

    fun processPayload(payloadSize: Int): Int {
        // Payload Limits: Throw TOO_MUCH_DATA for inputs > 32KB
        if (payloadSize > 32 * 1024) {
            return TOO_MUCH_DATA
        }
        return 0
    }

    fun checkHardwareSupport(algorithm: String, keySize: Int): Int {
        // Hardware Fallback: route unsupported back to physical fallback HAL
        if (algorithm == "RSA" && keySize > 2048) {
            return HARDWARE_FALLBACK_REQUIRED
        }
        return 0
    }
}
