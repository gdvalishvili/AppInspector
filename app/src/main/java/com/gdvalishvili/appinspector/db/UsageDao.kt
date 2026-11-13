package com.gdvalishvili.appinspector.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.ColumnInfo

/** Query result: total time per package. */
data class PackageTotal(
    @ColumnInfo(name = "package_name") val packageName: String,
    val ms: Long,
)

/** Query result: total time per day. */
data class DayTotal(val day: String, val ms: Long)

@Dao
abstract class UsageDao {

    @Upsert
    abstract suspend fun upsert(rows: List<DailyUsageEntity>)

    @Query("DELETE FROM daily_usage WHERE day = :day")
    abstract suspend fun deleteDay(day: String)

    /** Rewrites one day completely (safe to run again for the same day). */
    @Transaction
    open suspend fun replaceDay(day: String, rows: List<DailyUsageEntity>) {
        deleteDay(day)
        if (rows.isNotEmpty()) upsert(rows)
    }

    /** ISO dates (yyyy-MM-dd) compare correctly as text. */
    @Query("DELETE FROM daily_usage WHERE day < :minDay")
    abstract suspend fun deleteUsageOlderThan(minDay: String)

    @Query("DELETE FROM app_event WHERE time < :minTime")
    abstract suspend fun deleteEventsOlderThan(minTime: Long)

    @Query(
        "SELECT package_name, SUM(foreground_ms) AS ms FROM daily_usage " +
                "WHERE day BETWEEN :from AND :to GROUP BY package_name"
    )
    abstract suspend fun totalsByPackage(from: String, to: String): List<PackageTotal>

    @Query(
        "SELECT day, SUM(foreground_ms) AS ms FROM daily_usage " +
                "WHERE day BETWEEN :from AND :to AND package_name <> :excluded GROUP BY day"
    )
    abstract suspend fun totalsByDay(from: String, to: String, excluded: String): List<DayTotal>

    @Query("SELECT MIN(day) FROM daily_usage")
    abstract suspend fun firstDay(): String?

    @Query("SELECT * FROM app_event WHERE time >= :fromMs AND time < :toMs ORDER BY time DESC")
    abstract suspend fun eventsBetween(fromMs: Long, toMs: Long): List<AppEventEntity>

    /** Name of an app that is no longer installed, from the journal. */
    @Query("SELECT label FROM app_event WHERE package_name = :packageName ORDER BY time DESC LIMIT 1")
    abstract suspend fun lastKnownLabel(packageName: String): String?
}