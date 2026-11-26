package com.warden.app.core.data

import androidx.core.util.AtomicFile
import com.warden.app.data.models.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Single-process atomic JSON file implementation of [SettingsRepository].
 *
 * Uses [AtomicFile] on `filesDir/datastore/settings.json` to preserve exact `baseline-v1`
 * file path compatibility while eliminating native JNI binaries (`libdatastore_shared_counter.so`)
 * and avoiding main-thread disk I/O.
 */
class JsonSettingsRepository(
    private val settingsFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onError: (Exception) -> Unit = {},
) : SettingsRepository {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _settings = MutableStateFlow(Settings())
    override val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Volatile
    private var loaded = false

    init {
        scope.launch {
            mutex.withLock { ensureLoadedLocked() }
        }
    }

    override suspend fun update(transform: (Settings) -> Settings): Settings =
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                val updated = transform(_settings.value)
                writeAtomic(updated)
                _settings.value = updated
                updated
            }
        }

    override suspend fun exportJson(
        includePassword: Boolean,
        includeApiKey: Boolean,
    ): String =
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                val snapshot =
                    SettingsJsonCodec.prepareForExport(
                        current = _settings.value,
                        includePassword = includePassword,
                        includeApiKey = includeApiKey,
                    )
                SettingsJsonCodec.encode(snapshot)
            }
        }

    override suspend fun importJson(json: String): Boolean =
        withContext(ioDispatcher) {
            val imported = SettingsJsonCodec.decodeOrNull(json) ?: return@withContext false
            mutex.withLock {
                ensureLoadedLocked()
                val merged = SettingsJsonCodec.mergeImported(_settings.value, imported)
                writeAtomic(merged)
                _settings.value = merged
            }
            true
        }

    /** Ensures disk state has been loaded at least once before any read/modify/write cycle. */
    suspend fun awaitLoaded(): Settings =
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                _settings.value
            }
        }

    private fun ensureLoadedLocked() {
        if (loaded) return
        val restored = readAtomicOrNull() ?: Settings()
        _settings.value = restored
        loaded = true
    }

    private fun readAtomicOrNull(): Settings? {
        if (!settingsFile.exists()) return null
        return try {
            val raw = AtomicFile(settingsFile).readFully().decodeToString()
            SettingsJsonCodec.decodeOrNull(raw)
        } catch (e: IOException) {
            onError(e)
            null
        }
    }

    private fun writeAtomic(settings: Settings) {
        settingsFile.parentFile?.mkdirs()
        val atomicFile = AtomicFile(settingsFile)
        val stream = atomicFile.startWrite()
        try {
            stream.write(SettingsJsonCodec.encode(settings).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            atomicFile.failWrite(stream)
            onError(e)
            throw e
        }
    }
}
