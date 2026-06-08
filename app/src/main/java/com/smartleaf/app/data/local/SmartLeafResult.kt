package com.smartleaf.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smartleaf_results")
data class SmartLeafResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val maturityPhase: String,
    val qualityGrade: String,
    val confidence: Float,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val estimatedHarvestDays: Int,
    val isCloudSynced: Boolean = false,
    val imageUri: String? = null,
    val moisture: Float = 0f,
    val colorCode: String = "",
    val scanName: String = "",
    val userId: Long = 0
)
