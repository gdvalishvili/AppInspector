package com.gdvalishvili.appinspector.data.privacy

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.gdvalishvili.appinspector.data.background.BackgroundRepository
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

/** Result for one app. trackers: from the cached scan; risk: recomputed every time. */
data class PrivacyResult(
    val packageName: String,
    val trackers: List<TrackerDef>,
    val grantedSensitive: Set<String>,
    val risk: RiskResult,
    val skippedDex: Int,   // > 0: part of the code was too big to check
    val scannedAt: Long,
)

/**
 * Scans apps of this profile (work profile apps are not scanned).
 * The tracker part is cached per app version, so a second scan only checks new/updated apps.
 */
class PrivacyRepository(private val context: Context) {

    private val pm = context.packageManager
    private val cache = PrivacyCache(File(context.filesDir, "tracker_cache.json"))
    private val background = BackgroundRepository(context)

    /** Only what is already cached (no APK reading): fast, used when the screen opens. */
    suspend fun cached(apps: List<AppEntry>): Map<String, PrivacyResult> =
        withContext(Dispatchers.IO) { build(apps, scanMissing = false, onProgress = null) }

    /** Scans apps that are new, updated or never scanned. onProgress(done, total). */
    suspend fun scan(apps: List<AppEntry>, onProgress: (Int, Int) -> Unit): Map<String, PrivacyResult> =
        withContext(Dispatchers.IO) { build(apps, scanMissing = true, onProgress = onProgress) }

    private suspend fun build(
        apps: List<AppEntry>,
        scanMissing: Boolean,
        onProgress: ((Int, Int) -> Unit)?,
    ): Map<String, PrivacyResult> {
        val trackers = TrackerDatabase.load(context)
        val byId = trackers.associateBy { it.id }
        val matcher by lazy { TrackerMatcher(trackers) }
        val targets = apps.filter { it.profile == AppProfile.THIS && !it.isArchived }
        val network = background.load(targets.map { it.packageName }).network
        val entries = cache.read()
        val out = HashMap<String, PrivacyResult>()
        var changed = false

        targets.forEachIndexed { index, app ->
            coroutineContext.ensureActive() // leaving the screen stops the scan
            onProgress?.invoke(index, targets.size)
            val info = packageInfo(app.packageName) ?: return@forEachIndexed
            val versionCode = PackageInfoCompat.getLongVersionCode(info)
            var entry = entries[app.packageName]?.takeIf {
                it.versionCode == versionCode && it.lastUpdateTime == info.lastUpdateTime && it.dbVersion == TRACKER_DB_VERSION
            }
            if (entry == null && scanMissing) {
                val ai = info.applicationInfo ?: return@forEachIndexed
                val paths = listOfNotNull(ai.sourceDir) + ai.splitSourceDirs.orEmpty()
                val scan = ApkClassScanner.scan(paths)
                val found = matcher.match(scan.classNames)
                entry = CacheEntry(
                    versionCode, info.lastUpdateTime, TRACKER_DB_VERSION,
                    found.map { it.id }, scan.skippedDex, System.currentTimeMillis(),
                )
                entries[app.packageName] = entry
                changed = true
            }
            if (entry != null) {
                val found = entry.trackerIds.mapNotNull { byId[it] }
                val granted = grantedSensitive(info)
                val fgsTypes = background.capabilitiesOf(app.packageName)?.fgsTypes.orEmpty()
                val risk = RiskRules.evaluate(
                    RiskInput(found, granted, network[app.packageName]?.backgroundBytes ?: 0L, fgsTypes)
                )
                out[app.key] = PrivacyResult(app.packageName, found, granted, risk, entry.skippedDex, entry.scannedAt)
            }
        }
        onProgress?.invoke(targets.size, targets.size)
        if (changed) {
            val installed = targets.mapTo(HashSet()) { it.packageName }
            cache.write(entries.filterKeys { it in installed }) // forget uninstalled apps
        }
        return out
    }

    private fun grantedSensitive(info: PackageInfo): Set<String> {
        val names = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: return emptySet()
        return names.indices
            .filter { i -> i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { names[it] }
            .filterTo(HashSet()) { it in RiskRules.SENSITIVE_PERMISSIONS }
    }

    private fun packageInfo(packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        /** Bump when assets/trackers.json is replaced: forces a rescan of every app. */
        const val TRACKER_DB_VERSION = 1
    }
}

data class CacheEntry(
    val versionCode: Long,
    val lastUpdateTime: Long,
    val dbVersion: Int,
    val trackerIds: List<Int>,
    val skippedDex: Int,
    val scannedAt: Long,
)

/** files/tracker_cache.json: packageName -> CacheEntry. Written via temp file + rename. */
class PrivacyCache(private val file: File) {

    fun read(): MutableMap<String, CacheEntry> {
        if (!file.exists()) return HashMap()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8)).getJSONObject("entries")
            val out = HashMap<String, CacheEntry>()
            for (pkg in root.keys()) {
                val o = root.getJSONObject(pkg)
                val ids = o.getJSONArray("ids")
                out[pkg] = CacheEntry(
                    versionCode = o.getLong("vc"),
                    lastUpdateTime = o.getLong("upd"),
                    dbVersion = o.getInt("db"),
                    trackerIds = List(ids.length()) { ids.getInt(it) },
                    skippedDex = o.optInt("skipped"),
                    scannedAt = o.getLong("at"),
                )
            }
            out
        } catch (e: Exception) {
            HashMap() // broken cache: start again
        }
    }

    fun write(entries: Map<String, CacheEntry>) {
        val root = JSONObject()
        entries.forEach { (pkg, e) ->
            root.put(pkg, JSONObject().apply {
                put("vc", e.versionCode)
                put("upd", e.lastUpdateTime)
                put("db", e.dbVersion)
                put("ids", JSONArray(e.trackerIds))
                put("skipped", e.skippedDex)
                put("at", e.scannedAt)
            })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("entries", root).toString(), Charsets.UTF_8)
        tmp.renameTo(file)
    }
}