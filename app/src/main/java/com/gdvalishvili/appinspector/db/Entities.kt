package com.gdvalishvili.appinspector.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Event types stored in app_event.type */
object EventType {
    const val INSTALLED = "INSTALLED"
    const val UPDATED = "UPDATED"
    const val REMOVED = "REMOVED"
}

/** Last known state of each installed app. PK = (package_name, profile). */
@Entity(tableName = "app_state", primaryKeys = ["package_name", "profile"])
data class AppStateEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    val profile: String,                       // THIS / WORK
    val label: String,
    val version: String?,
    @ColumnInfo(name = "installed_at") val installedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
)

/** Journal of installs / updates / removals. */
@Entity(
    tableName = "app_event",
    indices = [Index(value = ["package_name", "profile"]), Index(value = ["time"])],
)
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    val profile: String,
    val label: String,
    val version: String?,
    @ColumnInfo(name = "installed_at") val installedAt: Long?,
    val type: String,                          // EventType.*
    val approx: Boolean,                       // true = time is approximate (detected later)
)

/** Foreground time per app per day. Filled in stage 2.0-b; created now to avoid a migration. */
@Entity(tableName = "daily_usage", primaryKeys = ["day", "package_name"])
data class DailyUsageEntity(
    val day: String,                           // ISO date, e.g. 2026-11-13
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
)