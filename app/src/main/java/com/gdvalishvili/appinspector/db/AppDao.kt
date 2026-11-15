package com.gdvalishvili.appinspector.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppDao {

    @Query("SELECT * FROM app_state")
    abstract suspend fun allStates(): List<AppStateEntity>

    @Query("SELECT * FROM app_state WHERE package_name = :packageName AND profile = :profile")
    abstract suspend fun state(packageName: String, profile: String): AppStateEntity?

    @Upsert
    abstract suspend fun upsertStates(states: List<AppStateEntity>)

    @Delete
    abstract suspend fun deleteStates(states: List<AppStateEntity>)

    @Insert
    abstract suspend fun insertEvents(events: List<AppEventEntity>)

    /** All changes of one sync in a single transaction. */
    @Transaction
    open suspend fun applySync(
        upserts: List<AppStateEntity>,
        deletes: List<AppStateEntity>,
        events: List<AppEventEntity>,
    ) {
        if (upserts.isNotEmpty()) upsertStates(upserts)
        if (deletes.isNotEmpty()) deleteStates(deletes)
        if (events.isNotEmpty()) insertEvents(events)
    }

    /** Latest removal per app (an app removed twice appears once). */
    @Query(
        """
        SELECT * FROM app_event e
        WHERE e.type = 'REMOVED'
          AND e.time = (SELECT MAX(x.time) FROM app_event x
                        WHERE x.package_name = e.package_name
                          AND x.profile = e.profile
                          AND x.type = 'REMOVED')
        ORDER BY e.time DESC
        """
    )
    abstract fun latestRemovals(): Flow<List<AppEventEntity>>
}