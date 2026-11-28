package com.warden.app.feature.websites.data

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Lazily loads and caches the shipped MIT-licensed adult-domain blocklist from
 * `assets/blocklists/porn_domains.txt` off the main thread.
 *
 * Maintains a single shared in-memory `Set<String>` (for O(1) background lookups with zero
 * redundant copies) and a sorted `List<String>` (for UI browsing only when opened).
 */
class PornDomainRepository(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val assetPath: String = DEFAULT_ASSET_PATH,
) {
    @Volatile
    private var cachedList: List<String>? = null

    @Volatile
    private var cachedSet: Set<String>? = null

    /** Returns the already-loaded built-in domains if cached, or an empty list if not yet loaded. */
    fun cachedDomainsOrEmpty(): List<String> = cachedList ?: emptyList()

    /** Returns the already-loaded O(1) lookup set if cached, or an empty set if not yet loaded. */
    fun cachedDomainSetOrEmpty(): Set<String> = cachedSet ?: emptySet()

    /** Loads and caches the sorted built-in domain list on [ioDispatcher]. */
    suspend fun loadBuiltinDomains(): List<String> {
        cachedList?.let { return it }
        return withContext(ioDispatcher) {
            ensureLoaded()
            cachedList ?: emptyList()
        }
    }

    /** Loads and caches the O(1) built-in domain lookup set on [ioDispatcher]. */
    suspend fun loadBuiltinDomainSet(): Set<String> {
        cachedSet?.let { return it }
        return withContext(ioDispatcher) {
            ensureLoaded()
            cachedSet ?: emptySet()
        }
    }

    private fun ensureLoaded() {
        if (cachedList != null && cachedSet != null) return
        val loaded = readAssetLines()
        val lookup = HashSet<String>(loaded.size)
        lookup.addAll(loaded)
        cachedSet = lookup
        cachedList = loaded
    }

    private fun readAssetLines(): List<String> =
        try {
            appContext.assets.open(assetPath).bufferedReader().useLines { lines ->
                val out = ArrayList<String>(EXPECTED_DOMAIN_CAPACITY)
                lines.forEach { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
                        out.add(trimmed)
                    }
                }
                out
            }
        } catch (_: IOException) {
            emptyList()
        }

    companion object {
        private const val DEFAULT_ASSET_PATH = "blocklists/porn_domains.txt"
        private const val EXPECTED_DOMAIN_CAPACITY = 48_000
    }
}
