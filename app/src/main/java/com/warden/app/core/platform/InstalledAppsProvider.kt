package com.warden.app.core.platform

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lightweight descriptor for an installed launchable app shown in dropdowns.
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
 * Off-main-thread [PackageManager] wrapper with a bounded [LruCache] (max 64 entries)
 * so app icon caching never grows unbounded on low-RAM devices.
 */
class InstalledAppsProvider(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val iconCache = LruCache<String, ResolvedAppInfo>(MAX_CACHE_ENTRIES)

    /** Returns all launchable installed apps excluding Warden, sorted alphabetically. */
    suspend fun loadLaunchableApps(): List<LaunchableApp> =
        withContext(ioDispatcher) {
            val pm = appContext.packageManager
            val selfPackage = appContext.packageName
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != selfPackage && pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { LaunchableApp(label = it.loadLabel(pm).toString(), packageName = it.packageName) }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
                .toList()
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
    }
}
