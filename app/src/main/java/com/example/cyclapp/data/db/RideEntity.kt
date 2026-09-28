package com.example.cyclapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0,
    val movingTimeMs: Long = 0L,
    val elapsedTimeMs: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val maxAltitudeMeters: Double? = null,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val routeName: String? = null
)