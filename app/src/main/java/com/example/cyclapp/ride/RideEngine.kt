package com.example.cyclapp.ride

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cyclapp.data.db.RideDao
import com.example.cyclapp.data.db.RideEntity
import com.example.cyclapp.data.db.TrackPointEntity
import com.example.cyclapp.util.GeoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class RideMetrics(
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val gradientPercent: Double? = null,
    val heartRate: Int? = null,
    val isAutoPaused: Boolean = false
)

class RideEngine(
    private val dao: RideDao,
    private val scope: CoroutineScope
) {
    enum class State { IDLE, RECORDING, PAUSED, FINISHED }

    var state = State.IDLE
        private set
    var rideId = 0L
        private set

    private val autoPauseController = AutoPauseController()
    var isAutoPaused by mutableStateOf(false)
        private set

    private var previous: Location? = null
    private var distance = 0.0
    private var gain = 0.0
    private var loss = 0.0
    private var maxAltitude: Double? = null
    private var maxSpeed = 0.0
    private var speedSum = 0.0
    private var speedCount = 0
    private var startTimeMs = 0L
    private var latestHr: Int? = null

    // StateFlow untuk diobservasi oleh Dashboard Overlay UI secara real-time
    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    fun setHeartRate(bpm: Int) {
        latestHr = bpm
        // Perbarui StateFlow metrik agar Dashboard UI menampilkan nilai BPM
        _metrics.value = _metrics.value.copy(heartRate = bpm)
    }

    fun onHeartRate(bpm: Int) = setHeartRate(bpm)

    suspend fun start(routeName: String? = null) {
        startTimeMs = System.currentTimeMillis()
        rideId = dao.insertRide(
            RideEntity(
                startTime = startTimeMs,
                routeName = routeName
            )
        )
        state = State.RECORDING
        autoPauseController.reset()
        isAutoPaused = false
        previous = null
        distance = 0.0
        gain = 0.0
        loss = 0.0
        maxAltitude = null
        maxSpeed = 0.0
        speedSum = 0.0
        speedCount = 0
        latestHr = null
        _metrics.value = RideMetrics()
    }

    fun pause() {
        if (state == State.RECORDING) state = State.PAUSED
    }

    fun resume() {
        if (state == State.PAUSED) state = State.RECORDING
    }

    fun onLocation(location: Location) {
        val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val now = System.currentTimeMillis()

        // 1. Evaluasi Auto Pause / Resume
        val action = autoPauseController.update(
            speedMps = speedMps,
            now = now,
            isRecording = (state == State.RECORDING),
            isPaused = (state == State.PAUSED)
        )

        when (action) {
            AutoPauseController.Action.PAUSE -> {
                pause()
                isAutoPaused = true
                return
            }
            AutoPauseController.Action.RESUME -> {
                resume()
                isAutoPaused = false
            }
            AutoPauseController.Action.NONE -> {
                if (state != State.RECORDING) return
            }
        }

        val old = previous
        var gradient: Double? = null
        var currentSpeedMps = 0.0

        if (old != null) {
            val d = GeoUtils.distanceMeters(
                old.latitude, old.longitude,
                location.latitude, location.longitude
            )
            // Filter perubahan GPS ekstrem
            if (d in 0.5..100.0) {
                distance += d
                val dt = (location.time - old.time).coerceAtLeast(1L) / 1000.0
                val speed = if (location.hasSpeed()) location.speed.toDouble() else d / dt
                currentSpeedMps = speed

                speedSum += speed
                speedCount++
                maxSpeed = maxOf(maxSpeed, speed)

                if (location.hasAltitude() && old.hasAltitude()) {
                    val dz = location.altitude - old.altitude
                    if (abs(dz) < 10.0) {
                        if (dz > 0) gain += dz else loss += -dz
                    }
                    if (d > 3.0) {
                        gradient = GeoUtils.gradientPercent(d, dz)
                    }
                }
            }
        }

        if (location.hasAltitude()) {
            maxAltitude = maxOf(maxAltitude ?: location.altitude, location.altitude)
        }

        val speedToSave = if (location.hasSpeed()) location.speed.toDouble() else null

        // Perbarui StateFlow Metrik Real-Time untuk UI Dashboard
        val durationSec = TimeUnit.MILLISECONDS.toSeconds(now - startTimeMs)
        val avgSpeedMps = if (speedCount > 0) speedSum / speedCount else 0.0

        _metrics.value = RideMetrics(
            durationSeconds = durationSec,
            distanceMeters = distance,
            currentSpeedKmh = currentSpeedMps * 3.6, // Konversi m/s ke km/h
            avgSpeedKmh = avgSpeedMps * 3.6,         // Konversi m/s ke km/h
            elevationGainMeters = gain,
            gradientPercent = gradient,
            heartRate = latestHr,
            isAutoPaused = isAutoPaused
        )

        scope.launch(Dispatchers.IO) {
            dao.insertTrackPoint(
                TrackPointEntity(
                    rideId = rideId,
                    timestamp = location.time,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    speedMps = speedToSave,
                    heartRate = latestHr, // Pass data BPM ke Room DB
                    gradientPercent = gradient
                )
            )
        }
        previous = location
    }

    suspend fun finish() {
        if (state == State.IDLE || state == State.FINISHED) return
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
                avgSpeedMps = if (speedCount > 0) speedSum / speedCount else 0.0,
                maxSpeedMps = maxSpeed
            )
        )
        state = State.FINISHED
    }
}
