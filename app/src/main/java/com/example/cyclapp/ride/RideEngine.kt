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
    val altitudeMeters: Double? = null,
    val gradientPercent: Double? = null,
    val heartRate: Int? = null,
    val isAutoPaused: Boolean = false,
    val isAutoPauseEnabled: Boolean = true
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
    var isManualPaused = false
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
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L
    private var latestHr: Int? = null

    // StateFlow untuk diobservasi oleh Dashboard Overlay UI secara real-time
    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    fun setHeartRate(bpm: Int) {
        latestHr = bpm
        _metrics.value = _metrics.value.copy(heartRate = bpm)
        ActiveRideRepository.updateMetrics(_metrics.value)
    }

    fun onHeartRate(bpm: Int) = setHeartRate(bpm)

    fun setAutoPauseEnabled(enabled: Boolean) {
        autoPauseController.isEnabled = enabled
        _metrics.value = _metrics.value.copy(isAutoPauseEnabled = enabled)
        ActiveRideRepository.updateMetrics(_metrics.value)

        // Jika Auto Pause dinonaktifkan saat sedang Auto Paused, langsung Resume
        if (!enabled && isAutoPaused && state == State.PAUSED) {
            resume()
        }
    }

    suspend fun start(routeName: String? = null) {
        startTimeMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        pauseStartMs = 0L
        isManualPaused = false
        isAutoPaused = false
        rideId = dao.insertRide(
            RideEntity(
                startTime = startTimeMs,
                routeName = routeName
            )
        )
        state = State.RECORDING
        autoPauseController.reset()
        previous = null
        distance = 0.0
        gain = 0.0
        loss = 0.0
        maxAltitude = null
        maxSpeed = 0.0
        speedSum = 0.0
        speedCount = 0
        latestHr = null
        _metrics.value = RideMetrics(isAutoPauseEnabled = autoPauseController.isEnabled)
        ActiveRideRepository.updateMetrics(_metrics.value)
    }

    fun pause() {
        if (state == State.RECORDING) {
            state = State.PAUSED
            isManualPaused = true
            isAutoPaused = false
            pauseStartMs = System.currentTimeMillis()
            updateMetrics(0.0, null, null)
        }
    }

    fun resume() {
        if (state == State.PAUSED) {
            state = State.RECORDING
            isManualPaused = false
            isAutoPaused = false
            if (pauseStartMs > 0L) {
                pausedDurationMs += System.currentTimeMillis() - pauseStartMs
                pauseStartMs = 0L
            }
            autoPauseController.reset()
            updateMetrics(0.0, null, null)
        }
    }

    private fun updateMetrics(currentSpeedMps: Double, altitude: Double?, gradient: Double?) {
        val now = System.currentTimeMillis()
        val activeTimeMs = if (state == State.RECORDING) {
            now - startTimeMs - pausedDurationMs
        } else {
            val currentPauseDuration = if (pauseStartMs > 0L) now - pauseStartMs else 0L
            now - startTimeMs - pausedDurationMs - currentPauseDuration
        }.coerceAtLeast(0L)

        val durationSec = TimeUnit.MILLISECONDS.toSeconds(activeTimeMs)
        val avgSpeedMps = if (speedCount > 0) speedSum / speedCount else 0.0

        val metrics = RideMetrics(
            durationSeconds = durationSec,
            distanceMeters = distance,
            currentSpeedKmh = currentSpeedMps * 3.6,
            avgSpeedKmh = avgSpeedMps * 3.6,
            altitudeMeters = altitude,
            gradientPercent = gradient,
            heartRate = latestHr,
            isAutoPaused = isAutoPaused,
            isAutoPauseEnabled = autoPauseController.isEnabled
        )
        _metrics.value = metrics
        ActiveRideRepository.updateMetrics(metrics)
    }

    fun onLocation(location: Location) {
        val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val now = System.currentTimeMillis()

        // 1. Evaluasi Auto Pause / Resume
        val action = autoPauseController.update(
            speedMps = speedMps,
            now = now,
            isRecording = (state == State.RECORDING),
            isPaused = (state == State.PAUSED),
            isManualPaused = isManualPaused
        )

        when (action) {
            AutoPauseController.Action.PAUSE -> {
                if (state == State.RECORDING) {
                    state = State.PAUSED
                    isAutoPaused = true
                    isManualPaused = false
                    pauseStartMs = now
                    updateMetrics(0.0, if (location.hasAltitude()) location.altitude else null, null)
                }
                return
            }
            AutoPauseController.Action.RESUME -> {
                if (state == State.PAUSED && isAutoPaused) {
                    state = State.RECORDING
                    isAutoPaused = false
                    isManualPaused = false
                    if (pauseStartMs > 0L) {
                        pausedDurationMs += now - pauseStartMs
                        pauseStartMs = 0L
                    }
                    autoPauseController.reset()
                    updateMetrics(speedMps, if (location.hasAltitude()) location.altitude else null, null)
                }
            }
            AutoPauseController.Action.NONE -> {
                if (state != State.RECORDING) {
                    updateMetrics(0.0, if (location.hasAltitude()) location.altitude else null, null)
                    return
                }
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
        val altitudeToSave = if (location.hasAltitude()) location.altitude else null

        updateMetrics(currentSpeedMps, altitudeToSave, gradient)

        scope.launch(Dispatchers.IO) {
            dao.insertTrackPoint(
                TrackPointEntity(
                    rideId = rideId,
                    timestamp = location.time,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = altitudeToSave,
                    speedMps = speedToSave,
                    heartRate = latestHr,
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

        val activeTimeMs = end - startTimeMs - pausedDurationMs
        dao.updateRide(
            old.copy(
                endTime = end,
                elapsedTimeMs = activeTimeMs.coerceAtLeast(0L),
                distanceMeters = distance,
                elevationGainMeters = gain,
                elevationLossMeters = loss,
                maxAltitudeMeters = maxAltitude,
                avgSpeedMps = if (speedCount > 0) speedSum / speedCount else 0.0,
                maxSpeedMps = maxSpeed
            )
        )
        state = State.FINISHED
        ActiveRideRepository.updateMetrics(RideMetrics())
    }
}
