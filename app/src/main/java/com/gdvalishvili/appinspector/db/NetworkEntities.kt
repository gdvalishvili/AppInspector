package com.gdvalishvili.appinspector.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/** SQL analogy: daily_network(day, packageName) PK -> background / foreground bytes (Wi-Fi + mobile). */
@Entity(tableName = "daily_network", primaryKeys = ["day", "packageName"])
data class DailyNetworkEntity(
    val day: String,          // ISO yyyy-MM-dd, local time zone
    val packageName: String,
    val bgBytes: Long,
    val fgBytes: Long,
)

data class PackageNetTotal(val packageName: String, val bgBytes: Long, val fgBytes: Long)

@Dao
interface NetworkDao {

    @Upsert
    suspend fun upsertAll(rows: List<DailyNetworkEntity>)

    @Query("SELECT MAX(day) FROM daily_network")
    suspend fun lastDay(): String?

    @Query("DELETE FROM daily_network WHERE day < :day")
    suspend fun deleteOlderThan(day: String)

    @Query(
        "SELECT packageName, SUM(bgBytes) AS bgBytes, SUM(fgBytes) AS fgBytes FROM daily_network " +
                "WHERE day BETWEEN :from AND :to GROUP BY packageName"
    )
    suspend fun totalsByPackage(from: String, to: String): List<PackageNetTotal>

    @Query("SELECT * FROM daily_network WHERE packageName = :packageName AND day BETWEEN :from AND :to ORDER BY day")
    suspend fun daysForPackage(packageName: String, from: String, to: String): List<DailyNetworkEntity>
}