package cleveres.tricky.cleverestech.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.logging.Level
import java.util.logging.Logger

class LoggerConfigTest {

    private val nanoLoggerName = "fi.iki.elonen.NanoHTTPD"
    private var originalLevel: Level? = null

    @Before
    fun setUp() {
        val logger = Logger.getLogger(nanoLoggerName)
        originalLevel = logger.level
    }

    @After
    fun tearDown() {
        val logger = Logger.getLogger(nanoLoggerName)
        logger.level = originalLevel
    }

    @Test
    fun disableNanoHttpdLogging_setsLevelToOff() {
        // Arrange
        val logger = Logger.getLogger(nanoLoggerName)
        logger.level = Level.ALL // Set to something other than OFF

        // Act
        LoggerConfig.disableNanoHttpdLogging()

        // Assert
        assertEquals(Level.OFF, logger.level)
    }
}
