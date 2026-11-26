package com.warden.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded on-device crash and non-fatal error logger.
 *
 * Resolves `filesDir` lazily so constructing and registering this handler in [Warden.onCreate]
 * performs zero disk I/O on the main thread. Automatically rotates `crash_log.txt` into a single
 * `crash_log.prev.txt` backup once it exceeds [maxLogBytes] (128 KiB), strictly bounding storage.
 */
class CrashLogger(
    private val context: Context,
    private val maxLogBytes: Long = DEFAULT_MAX_LOG_BYTES
) : Thread.UncaughtExceptionHandler {

    private val writeLock = Any()
    private val logFile: File by lazy { File(context.filesDir, LOG_FILE_NAME) }
    private val backupLogFile: File by lazy { File(context.filesDir, BACKUP_LOG_FILE_NAME) }
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        appendEntry(
            header = "Crash at ${formatTimestamp()}",
            metadata = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
            throwable = throwable
        )
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /** Appends a caught non-fatal [exception] to the rotated on-device log file. */
    fun logNonFatalError(exception: Exception) {
        appendEntry(
            header = "Non-Fatal Error (Caught) at ${formatTimestamp()}",
            metadata = null,
            throwable = exception
        )
    }

    private fun appendEntry(header: String, metadata: String?, throwable: Throwable) {
        synchronized(writeLock) {
            try {
                rotateIfNeeded()
                FileOutputStream(logFile, true).use { fos ->
                    PrintWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { pw ->
                        pw.append("\n--- ").append(header).append(" ---\n")
                        if (metadata != null) {
                            pw.append(metadata).append('\n')
                        }
                        throwable.printStackTrace(pw)
                        pw.flush()
                    }
                }
            } catch (_: IOException) {
                // Disk full or unavailable; never crash inside the crash handler itself.
            }
        }
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() >= maxLogBytes) {
            if (backupLogFile.exists()) {
                backupLogFile.delete()
            }
            if (!logFile.renameTo(backupLogFile)) {
                logFile.delete()
            }
        }
    }

    private fun formatTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    companion object {
        private const val LOG_FILE_NAME = "crash_log.txt"
        private const val BACKUP_LOG_FILE_NAME = "crash_log.prev.txt"
        private const val DEFAULT_MAX_LOG_BYTES = 128L * 1024L
    }
}
