package com.gdvalishvili.appinspector.spike

// TEMPORARY spike screen: shows what Android actually returns on this device.
// Hardcoded strings on purpose: this file will be deleted after the spike.

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

// ---------- Data ----------

private data class Section(val title: String, val lines: List<String>)

private data class OtherProfileApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val details: String,
)

private data class SpikeData(
    val sections: List<Section>,
    val otherProfileApps: List<OtherProfileApp>,
)

// ---------- UI ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeScreen() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    // Load everything off the main thread; reload when refreshKey changes
    val data by produceState<SpikeData?>(initialValue = null, refreshKey) {
        value = null
        value = withContext(Dispatchers.Default) { collectSpikeData(context.applicationContext) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("App Inspector · spike") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refreshKey++ }) { Text("განახლება") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Usage access") }
                }
            }

            val current = data
            if (current == null) {
                item { CircularProgressIndicator() }
            } else {
                items(current.sections) { section -> SectionBlock(section) }

                item {
                    Text(
                        "7. სხვა პროფილის აპები (დააჭირე → App info)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (current.otherProfileApps.isEmpty()) {
                    item { Text("არცერთი (work profile დაპაუზებულია ან არ ჩანს)") }
                }
                items(current.otherProfileApps) { app ->
                    OtherProfileAppRow(app) { openAppInfo(context, app) }
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(section: Section) {
    Column(Modifier.fillMaxWidth()) {
        Text(section.title, style = MaterialTheme.typography.titleMedium)
        section.lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OtherProfileAppRow(app: OtherProfileApp, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(app.label, style = MaterialTheme.typography.bodyLarge)
        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
        Text(app.details, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun openAppInfo(context: Context, app: OtherProfileApp) {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    try {
        // Opens system "App info" for a package in another profile (e.g. work)
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "ვერ გაიხსნა: ${e.javaClass.simpleName}: ${e.message}",
            Toast.LENGTH_LONG,
        ).show()
    }
}

// ---------- Data collection ----------

private val THREE_YEARS_MS = TimeUnit.DAYS.toMillis(3 * 365L)

private fun collectSpikeData(context: Context): SpikeData {
    val otherApps = mutableListOf<OtherProfileApp>()
    val sections = listOf(
        safeSection("1. მოწყობილობა") { deviceLines() },
        safeSection("2. ამ პროფილის აპები (PackageManager)") { packageLines(context) },
        safeSection("3. Usage access") {
            listOf(
                if (hasUsageAccess(context)) "ჩართულია ✔"
                else "გამორთულია ✘ — დააჭირე 'Usage access', ჩართე App Inspector, დაბრუნდი და 'განახლება'"
            )
        },
        safeSection("4. გამოყენების ისტორიის სიღრმე") { usageDepthLines(context) },
        safeSection("5. ბოლო გამოყენება (lastTimeUsed)") { lastUsedLines(context) },
        safeSection("6. პროფილები (LauncherApps)") { profileLines(context, otherApps) },
    )
    return SpikeData(sections, otherApps)
}

// Never crash the spike: show the error text inside the section instead
private fun safeSection(title: String, block: () -> List<String>): Section =
    try {
        Section(title, block())
    } catch (e: Exception) {
        Section(title, listOf("შეცდომა: ${e.javaClass.simpleName}: ${e.message}"))
    }

private fun deviceLines(): List<String> = listOf(
    "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
    "${Build.MANUFACTURER} ${Build.MODEL}",
    "ეს პროფილი: ${Process.myUserHandle()}",
)

private fun packageLines(context: Context): List<String> {
    val packages = getInstalledPackagesCompat(context.packageManager)
    var user = 0
    var system = 0
    var updatedSystem = 0
    var archived = 0
    val userApps = mutableListOf<PackageInfo>()

    for (p in packages) {
        val ai = p.applicationInfo ?: continue
        when {
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 -> updatedSystem++
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> system++
            else -> {
                user++
                userApps += p
            }
        }
        if (isArchivedCompat(ai)) archived++
    }

    val lines = mutableListOf(
        "სულ: ${packages.size}",
        "მომხმარებლის: $user | სისტემური: $system | განახლებული სისტემური: $updatedSystem",
        "დაარქივებული: $archived",
        "— ბოლოს დაყენებული 5 მომხმარებლის აპი —",
    )
    userApps.sortedByDescending { it.firstInstallTime }.take(5).forEach { p ->
        lines += "${p.packageName}  v${p.versionName ?: "?"}"
        lines += "   დაყენ.: ${fmt(p.firstInstallTime)} | განახ.: ${fmt(p.lastUpdateTime)}"
    }
    return lines
}

private fun getInstalledPackagesCompat(pm: PackageManager): List<PackageInfo> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
            // Android 15+: also include archived apps
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES)
            )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        else -> {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
    }

private fun isArchivedCompat(ai: ApplicationInfo): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ai.isArchived

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    }
    return if (mode == AppOpsManager.MODE_DEFAULT) {
        context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        mode == AppOpsManager.MODE_ALLOWED
    }
}

private val USAGE_INTERVALS = listOf(
    "DAILY" to UsageStatsManager.INTERVAL_DAILY,
    "WEEKLY" to UsageStatsManager.INTERVAL_WEEKLY,
    "MONTHLY" to UsageStatsManager.INTERVAL_MONTHLY,
    "YEARLY" to UsageStatsManager.INTERVAL_YEARLY,
)

private fun usageDepthLines(context: Context): List<String> {
    if (!hasUsageAccess(context)) return listOf("Usage access გამორთულია")
    val usm = context.getSystemService(UsageStatsManager::class.java)
    val now = System.currentTimeMillis()
    return USAGE_INTERVALS.map { (name, interval) ->
        val stats = usm.queryUsageStats(interval, now - THREE_YEARS_MS, now)
        val oldest = stats.minOfOrNull { it.firstTimeStamp } ?: 0L
        "$name: ${stats.size} ჩანაწერი, უძველესი: ${fmtDate(oldest)}"
    }
}

// Max lastTimeUsed per package across all intervals
private fun collectLastUsed(context: Context): Map<String, Long> {
    val usm = context.getSystemService(UsageStatsManager::class.java)
    val now = System.currentTimeMillis()
    val result = HashMap<String, Long>()
    for ((_, interval) in USAGE_INTERVALS) {
        for (s in usm.queryUsageStats(interval, now - THREE_YEARS_MS, now)) {
            val t = s.lastTimeUsed
            if (t > (result[s.packageName] ?: 0L)) result[s.packageName] = t
        }
    }
    return result
}

private fun lastUsedLines(context: Context): List<String> {
    if (!hasUsageAccess(context)) return listOf("Usage access გამორთულია")
    val lastUsed = collectLastUsed(context)
    val userApps = getInstalledPackagesCompat(context.packageManager).filter { p ->
        val ai = p.applicationInfo
        ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    }
    val withRecord = userApps.filter { (lastUsed[it.packageName] ?: 0L) > 0L }
    val noRecord = userApps - withRecord.toSet()

    val lines = mutableListOf(
        "მომხმარებლის აპები: ${userApps.size} | ჩანაწერით: ${withRecord.size} | ჩანაწერის გარეშე: ${noRecord.size}",
        "— ყველაზე დიდი ხნის წინ გამოყენებული 10 —",
    )
    withRecord.sortedBy { lastUsed[it.packageName] }.take(10).forEach {
        lines += "${fmt(lastUsed[it.packageName] ?: 0L)}  ${it.packageName}"
    }
    lines += "— ჩანაწერის გარეშე (ყველაზე ძველი 10) —"
    noRecord.sortedBy { it.firstInstallTime }.take(10).forEach {
        lines += "დაყენ. ${fmtDate(it.firstInstallTime)}  ${it.packageName}"
    }
    lines += "— ბოლოს გამოყენებული 5 —"
    withRecord.sortedByDescending { lastUsed[it.packageName] }.take(5).forEach {
        lines += "${fmt(lastUsed[it.packageName] ?: 0L)}  ${it.packageName}"
    }
    return lines
}

private fun profileLines(context: Context, out: MutableList<OtherProfileApp>): List<String> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    val me = Process.myUserHandle()
    val profiles = launcherApps.profiles

    val lines = mutableListOf(
        "UserManager.userProfiles: ${userManager.userProfiles.size}",
        "LauncherApps.profiles: ${profiles.size}",
    )
    for (profile in profiles) {
        val isMe = profile == me
        val paused = !isMe && userManager.isQuietModeEnabled(profile)
        val apps = launcherApps.getActivityList(null, profile)
            .distinctBy { it.componentName.packageName }
        lines += "$profile ${if (isMe) "(ეს)" else "(სხვა)"} | დაპაუზებული: $paused | აპები: ${apps.size}"

        if (!isMe) {
            apps.sortedBy { it.label.toString().lowercase() }.forEach { a ->
                val ai = a.applicationInfo
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                out += OtherProfileApp(
                    label = a.label.toString(),
                    packageName = a.componentName.packageName,
                    component = a.componentName,
                    user = profile,
                    details = buildString {
                        append("დაყენ.: ${fmt(a.firstInstallTime)} | სისტემური: $isSystem")
                        append("\nვერსია APK-დან (ექსპ.): ${versionFromApk(context, ai)}")
                        append("\nAPK ფაილის თარიღი (ექსპ.): ${apkFileTime(ai)}")
                        if (isArchivedCompat(ai)) append("\nდაარქივებული")
                    },
                )
            }
        }
    }
    return lines
}

// Experiment: read version directly from the APK file of an app in another profile
private fun versionFromApk(context: Context, ai: ApplicationInfo): String {
    val path = ai.sourceDir ?: return "—"
    return try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(path, 0)
        }
        info?.versionName ?: "ვერ წავიკითხე"
    } catch (e: Exception) {
        "შეცდომა: ${e.javaClass.simpleName}"
    }
}

// Experiment: APK file modification time as a possible "last update" hint
private fun apkFileTime(ai: ApplicationInfo): String {
    val path = ai.sourceDir ?: return "—"
    val file = File(path)
    return if (file.canRead()) fmt(file.lastModified()) else "წვდომა არ არის"
}

// ---------- Formatting ----------

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

private fun fmt(ms: Long): String =
    if (ms <= 0L) "—" else dateTimeFormatter.format(Instant.ofEpochMilli(ms))

private fun fmtDate(ms: Long): String =
    if (ms <= 0L) "—" else dateFormatter.format(Instant.ofEpochMilli(ms))