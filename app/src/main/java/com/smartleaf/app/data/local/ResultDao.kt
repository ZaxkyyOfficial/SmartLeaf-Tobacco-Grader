package com.smartleaf.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Insert
    suspend fun insertResult(result: SmartLeafResult): Long

    @Query("SELECT * FROM smartleaf_results WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllResults(userId: Long): Flow<List<SmartLeafResult>>

    @Query("SELECT * FROM smartleaf_results WHERE userId = :userId AND isCloudSynced = 0")
    suspend fun getUnsyncedResults(userId: Long): List<SmartLeafResult>

    @Query("UPDATE smartleaf_results SET isCloudSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT * FROM smartleaf_results WHERE userId = :userId AND (scanName LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchResults(query: String, userId: Long): Flow<List<SmartLeafResult>>

    @Query("DELETE FROM smartleaf_results WHERE id = :id")
    suspend fun deleteResult(id: Long)

    @Query("UPDATE smartleaf_results SET scanName = :newName WHERE id = :id")
    suspend fun updateScanName(id: Long, newName: String)
}
