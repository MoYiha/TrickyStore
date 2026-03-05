package cleveres.tricky.cleverestech.util

import java.util.logging.Level
import java.util.logging.Logger

object LoggerConfig {
    fun disableNanoHttpdLogging() {
        val logger = Logger.getLogger("fi.iki.elonen.NanoHTTPD")
        logger.level = Level.OFF
    }
}
