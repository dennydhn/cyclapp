package com.example.cyclapp.ride

import android.location.Location
import com.example.cyclapp.data.db.RideDao
import com.example.cyclapp.data.db.RideEntity
import com.example.cyclapp.data.db.TrackPointEntity
import com.example.cyclapp.util.GeoUtils
import kotlinx.coroutines.*
import kotlin.math.abs

class RideEngine(
    private val dao: RideDao,
    private val scope: CoroutineScope
){
    enum class State {IDLE, RECORDING, PAUSED, FINISHED}

    var state = State.IDLE
        private set
    var rideId = 0L
        private set

    private var previous: Location? = null
    private var distance =0.0
    private var gain = 0.0
    private var loss = 0.0
    private var maxAltitude: Double?= null
    private var maxSpeed = 0.0
    private var speedSum = 0.0
    private var speedCount = 0

    suspend fun start(routeName: String? = null){
        rideId= dao.insertRide(
            RideEntity(
                startTime = System.currentTimeMillis(),
                routeName = routeName
            )
        )
        state = State.RECORDING
        previous = null
    }
    fun pause(){
        if (state == State.RECORDING) state = State.PAUSED
    }
    fun resume(){
        if (state== State.PAUSED) state = State.RECORDING
    }
    fun onLocation(location: Location){
        if (state != State.RECORDING) return

        val old = previous
        var gradient: Double? = null

        if (old!=null){
            val d = GeoUtils.distanceMeters(
                old.latitude, old.longitude,
                location.latitude, location.longitude
            )
            // filter perubahan gps extrem
            if (d in 0.5..100.0){
                distance += d
                val dt = (location.time - old.time).coerceAtLeast(1L)/1000.0
                val speed = if (location.hasSpeed()) location.speed.toDouble() else d/dt

                speedSum += speed
                speedCount++
                maxSpeed = maxOf(maxSpeed, speed)

                if (location.hasAltitude() && old.hasAltitude()){
                    val dz = location.altitude - old.altitude
                    if (abs(dz)<10.0){
                        if (dz>0) gain += dz else loss+= -dz
                    }
                    if (d>3.0){
                       gradient = GeoUtils.gradientPercent(d, dz)
                    }
                }
            }
        }
        if (location.hasAltitude()){
            maxAltitude = maxOf(maxAltitude ?: location.altitude, location.altitude)
        }
        val speed = if (location.hasSpeed()) location.speed.toDouble() else null

        scope.launch(Dispatchers.IO){
            dao.insertTrackPoint(
                TrackPointEntity(
                    rideId = rideId,
                    timestamp = location.time,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    speedMps = speed,
                    heartRate = null,
                    gradientPercent = gradient
                )
            )
        }
        previous = location
    }
    suspend fun finish(){
        if (state == State.IDLE || state ==State.FINISHED) return
        val end = System.currentTimeMillis()
        val old = dao.getRide(rideId) ?: return

        dao.updateRide(
            old.copy(
                endTime = end,
                elapsedTimeMs = end - old.startTime,
                distanceMeters = distance,
                elevationGainMeters = gain,
                elevationLossMeters = loss,
                maxAltitudeMeters = maxAltitude,
                avgSpeedMps = if (speedCount>0) speedSum/speedCount else 0.0,
                maxSpeedMps = maxSpeed
            )
        )
    }

}