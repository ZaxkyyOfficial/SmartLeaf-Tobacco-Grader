package com.smartleaf.app.repository

import com.smartleaf.app.data.local.ResultDao
import com.smartleaf.app.data.local.SmartLeafResult
import com.smartleaf.app.data.remote.MockSyncService
import com.smartleaf.app.data.remote.SyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LeafRepository(private val dao: ResultDao) {
    private val syncService: SyncService = MockSyncService()

    fun getAllResults(userId: Long): Flow<List<SmartLeafResult>> {
        return dao.getAllResults(userId)
    }

    fun searchResults(query: String, userId: Long): Flow<List<SmartLeafResult>> {
        return dao.searchResults(query, userId)
    }

    suspend fun saveResult(result: SmartLeafResult): Long {
        return withContext(Dispatchers.IO) {
            dao.insertResult(result)
        }
    }

    suspend fun deleteResult(id: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteResult(id)
        }
    }

    suspend fun updateScanName(id: Long, newName: String) {
        withContext(Dispatchers.IO) {
            dao.updateScanName(id, newName)
        }
    }

    suspend fun syncPendingResults(userId: Long) {
        withContext(Dispatchers.IO) {
            val unsynced = dao.getUnsyncedResults(userId)
            if (unsynced.isNotEmpty()) {
                try {
                    val response = syncService.syncResults(unsynced)
                    if (response.success) {
                        dao.markAsSynced(unsynced.map { it.id })
                    }
                } catch (e: Exception) {
                    // Handle sync error, retry later
                }
            }
        }
    }
}
