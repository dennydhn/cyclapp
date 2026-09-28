package com.example.cyclapp.data.db

import androidx.room.*

@Dao
interface RideDao {
    @Insert
    suspend fun insertRide(ride: RideEntity): Long

    @Update
    suspend fun updateRide(ride: RideEntity)

    @Insert
    suspend fun insertTrackPoint(point: TrackPointEntity)

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    suspend fun getRides(): List<RideEntity>

    @Query("SELECT * FROM rides WHERE id = :id LIMIT 1")
    suspend fun getRide(id: Long): RideEntity?

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestamp")
    suspend fun getTrackPoints(rideId: Long): List<TrackPointEntity>
}