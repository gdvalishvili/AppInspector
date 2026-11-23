package com.gdvalishvili.appinspector.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.AutoMigration

@Database(
    entities = [AppStateEntity::class, AppEventEntity::class, DailyUsageEntity::class, DailyNetworkEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun usageDao(): UsageDao
    abstract fun networkDao(): NetworkDao
    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** One database instance per process. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_inspector.db",
                ).build().also { instance = it }
            }
    }
}