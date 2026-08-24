sed -i '/class KeyboxVerifierTest {/a \
    @Test\
    fun `clearMemoryCacheForTesting clears cache values`() {\
        val etagField = KeyboxVerifier::class.java.getDeclaredField("cachedEtag")\
        etagField.isAccessible = true\
        etagField.set(KeyboxVerifier, "some-etag")\
\
        val timeField = KeyboxVerifier::class.java.getDeclaredField("lastFetchTime")\
        timeField.isAccessible = true\
        timeField.set(KeyboxVerifier, 12345L)\
\
        KeyboxVerifier.clearMemoryCacheForTesting()\
\
        assertEquals(null, etagField.get(KeyboxVerifier))\
        assertEquals(0L, timeField.get(KeyboxVerifier))\
    }' ./service/src/test/java/cleveres/tricky/cleverestech/util/KeyboxVerifierTest.kt

./gradlew :service:testDebugUnitTest --tests "cleveres.tricky.cleverestech.util.KeyboxVerifierTest.clearMemoryCacheForTesting clears cache values"
