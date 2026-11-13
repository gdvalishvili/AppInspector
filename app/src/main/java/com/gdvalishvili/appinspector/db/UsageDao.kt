package com.gdvalishvili.appinspector.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

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
}