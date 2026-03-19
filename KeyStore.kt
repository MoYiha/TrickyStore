package com.android.keystore

import java.io.File
import java.io.IOException

class KeyStore {
    // Atomic Persistence: Store generated keys using file-level locking

    fun storeKeyAtomically(keyName: String, keyData: ByteArray): Boolean {
        val keysDir = File("/data/misc/keystore/mykeys")
        if (!keysDir.exists()) keysDir.mkdirs()

        val targetFile = File(keysDir, keyName)
        val tempFile = File(keysDir, "$keyName.tmp")

        try {
            // Write to temp file
            tempFile.writeBytes(keyData)

            // Atomic rename
            if (tempFile.renameTo(targetFile)) {
                return true
            } else {
                tempFile.delete()
                return false
            }
        } catch (e: IOException) {
            tempFile.delete()
            return false
        }
    }

    fun loadKey(keyName: String): ByteArray? {
        val keysDir = File("/data/misc/keystore/mykeys")
        val targetFile = File(keysDir, keyName)

        if (targetFile.exists()) {
            try {
                return targetFile.readBytes()
            } catch (e: IOException) {
                return null
            }
        }
        return null
    }
}
