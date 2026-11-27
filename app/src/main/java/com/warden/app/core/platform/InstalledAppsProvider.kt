package com.warden.app.core.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lightweight descriptor for an installed launchable app shown in pickers.
 */
data class LaunchableApp(
    val label: String,
    val packageName: String,
) {
    override fun toString(): String = label
}

/**
 * Resolved label and icon for displaying an app in the ignored-apps list.
 */
data class ResolvedAppInfo(
    val label: String,
    val icon: Drawable?,
)

/**
 * Off-main-thread [PackageManager] wrapper using a single-IPC launcher intent query with
 * a short-lived in-memory cache for launchable apps and a bounded [LruCache] (max 64 entries)
 * for app icons.
 */
class InstalledAppsProvider(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val iconCache = LruCache<String, ResolvedAppInfo>(MAX_CACHE_ENTRIES)

    @Volatile
    private var cachedApps: List<LaunchableApp>? = null

    @Volatile
    private var cachedAppsAtMs: Long = 0L

    /** Returns the warm in-memory launchable apps snapshot if fresh, or `null` otherwise. */
    fun cachedLaunchableAppsOrNull(): List<LaunchableApp>? {
        val snapshot = cachedApps ?: return null
        val ageMs = SystemClock.elapsedRealtime() - cachedAppsAtMs
        return if (ageMs in 0..LAUNCHABLE_CACHE_TTL_MS) snapshot else null
    }

    /**
     * Returns all launchable installed apps excluding Warden, sorted alphabetically.
     *
     * Uses a single [PackageManager.queryIntentActivities] IPC call instead of iterating every
     * installed package, and serves from an in-memory cache when fresh.
     */
    suspend fun loadLaunchableApps(forceRefresh: Boolean = false): List<LaunchableApp> {
        if (!forceRefresh) {
            cachedLaunchableAppsOrNull()?.let { return it }
        }
        return withContext(ioDispatcher) {
            val loaded = queryLaunchableApps()
            cachedApps = loaded
            cachedAppsAtMs = SystemClock.elapsedRealtime()
            loaded
        }
    }

    /** Resolves label and icon for [packageName] on [ioDispatcher], caching up to 64 entries. */
    suspend fun resolveAppInfo(packageName: String): ResolvedAppInfo {
        iconCache.get(packageName)?.let { return it }
        return withContext(ioDispatcher) {
            val resolved = queryPackageInfo(packageName)
            iconCache.put(packageName, resolved)
            resolved
        }
    }

    /** Returns the cached label for [packageName], or `null` if not yet resolved. */
    fun cachedLabel(packageName: String): String? = iconCache.get(packageName)?.label

    private fun queryLaunchableApps(): List<LaunchableApp> {
        val pm = appContext.packageManager
        val selfPackage = appContext.packageName
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seenPackages = HashSet<String>()
        return pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == selfPackage || !seenPackages.add(pkg)) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg }
                LaunchableApp(label = label, packageName = pkg)
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun queryPackageInfo(packageName: String): ResolvedAppInfo =
        try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            ResolvedAppInfo(label = info.loadLabel(pm).toString(), icon = info.loadIcon(pm))
        } catch (_: PackageManager.NameNotFoundException) {
            ResolvedAppInfo(
                label = packageName,
                icon = ContextCompat.getDrawable(appContext, android.R.drawable.sym_def_app_icon),
            )
        }

    companion object {
        private const val MAX_CACHE_ENTRIES = 64
        private const val LAUNCHABLE_CACHE_TTL_MS = 300_000L
    }
}
