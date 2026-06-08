package com.smartleaf.app.data.remote

import com.smartleaf.app.data.local.SmartLeafResult

// Mock networking service interface
interface SyncService {
    suspend fun syncResults(results: List<SmartLeafResult>): SyncResponse
}

data class SyncResponse(val success: Boolean, val message: String)

// Mock implementation for the prototype
class MockSyncService : SyncService {
    override suspend fun syncResults(results: List<SmartLeafResult>): SyncResponse {
        kotlinx.coroutines.delay(1000) // Simulate network delay
        return SyncResponse(true, "Synced ${results.size} items successfully")
    }
}
