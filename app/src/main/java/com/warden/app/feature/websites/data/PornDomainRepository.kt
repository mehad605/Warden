package com.warden.app.feature.websites.data

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Loads and caches the shipped MIT-licensed adult-domain blocklist from
 * `assets/blocklists/porn_domains.txt` lazily off the main thread.
 */
class PornDomainRepository(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val assetPath: String = DEFAULT_ASSET_PATH,
) {
    @Volatile
    private var cachedDomains: List<String>? = null

    /** Returns the already-loaded built-in domains if cached, or an empty list if not yet loaded. */
    fun cachedDomainsOrEmpty(): List<String> = cachedDomains ?: emptyList()

    /** Loads and caches the deduplicated built-in domain list on [ioDispatcher]. */
    suspend fun loadBuiltinDomains(): List<String> {
        cachedDomains?.let { return it }
        return withContext(ioDispatcher) {
            cachedDomains?.let { return@withContext it }
            val loaded = readAssetLines()
            cachedDomains = loaded
            loaded
        }
    }

    private fun readAssetLines(): List<String> =
        try {
            appContext.assets.open(assetPath).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith('#') }
                    .toList()
            }
        } catch (_: IOException) {
            emptyList()
        }

    companion object {
        private const val DEFAULT_ASSET_PATH = "blocklists/porn_domains.txt"
    }
}
