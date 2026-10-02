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
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val locationUpdateCount: Int = 0,
    val lastLocationTimeMs: Long = 0L
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
    private var latestAltitude: Double? = null
    private var previousAltitude: Double? = null
    private var currentSpeedMps: Double = 0.0
    private var latestGradient: Double? = null
    private var timerJob: Job? = null

    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var gpsAccuracy: Float? = null
    private var locationUpdateCount = 0
    private var lastLocationTimeMs = 0L

    // StateFlow untuk diobservasi oleh Dashboard Overlay UI secara real-time
    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && state == State.RECORDING) {
                delay(1000L)
                updateMetrics(currentSpeedMps, latestAltitude, latestGradient)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun setHeartRate(bpm: Int) {
        latestHr = bpm
        _metrics.value = _metrics.value.copy(heartRate = bpm)
        ActiveRideRepository.updateMetrics(_metrics.value)
    }

    fun onHeartRate(bpm: Int) = setHeartRate(bpm)

    suspend fun start(routeName: String? = null) {
        startTimeMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        pauseStartMs = 0L
        rideId = dao.insertRide(
            RideEntity(
                startTime = startTimeMs,
                routeName = routeName
            )
        )
        state = State.RECORDING
        previous = null
        distance = 0.0
        gain = 0.0
        loss = 0.0
        maxAltitude = null
        maxSpeed = 0.0
        speedSum = 0.0
        speedCount = 0
        latestHr = null
        latestAltitude = null
        previousAltitude = null
        currentSpeedMps = 0.0
        latestGradient = null
        currentLat = null
        currentLng = null
        gpsAccuracy = null
        locationUpdateCount = 0
        lastLocationTimeMs = 0L
        _metrics.value = RideMetrics()
        ActiveRideRepository.updateMetrics(_metrics.value)
        startTimer()
    }

    fun pause() {
        if (state == State.RECORDING) {
            state = State.PAUSED
            pauseStartMs = System.currentTimeMillis()
            stopTimer()
            updateMetrics(0.0, latestAltitude, latestGradient)
        }
    }

    fun resume() {
        if (state == State.PAUSED) {
            state = State.RECORDING
            if (pauseStartMs > 0L) {
                pausedDurationMs += System.currentTimeMillis() - pauseStartMs
                pauseStartMs = 0L
            }
            startTimer()
            updateMetrics(0.0, latestAltitude, latestGradient)
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
            currentLat = currentLat,
            currentLng = currentLng,
            gpsAccuracyMeters = gpsAccuracy,
            locationUpdateCount = locationUpdateCount,
            lastLocationTimeMs = lastLocationTimeMs
        )
        _metrics.value = metrics
        ActiveRideRepository.updateMetrics(metrics)
    }

    fun onLocation(location: Location) {
        currentLat = location.latitude
        currentLng = location.longitude
        gpsAccuracy = if (location.hasAccuracy()) location.accuracy else null
        locationUpdateCount++
        lastLocationTimeMs = System.currentTimeMillis()

        val currentAltitude = if (location.hasAltitude()) location.altitude else latestAltitude

        if (state != State.RECORDING) {
            if (currentAltitude != null) {
                latestAltitude = currentAltitude
            }
            updateMetrics(0.0, latestAltitude, latestGradient)
            return
        }

        val old = previous
        var gradient: Double? = latestGradient
        var speedMpsCalculated = 0.0

        if (old == null) {
            // Titik awal perekaman
            previous = location
        } else {
            val d = GeoUtils.distanceMeters(
                old.latitude, old.longitude,
                location.latitude, location.longitude
            )
            val dt = ((location.time - old.time) / 1000.0).coerceAtLeast(0.1)
            val speed = if (location.hasSpeed() && location.speed > 0f) location.speed.toDouble() else d / dt

            // Filter kecepatan yang tidak masuk akal (teleportasi/glitch > 180 km/h atau 50 m/s)
            if (speed > 50.0) {
                // Reset 'previous' ke titik baru tanpa menambah jarak akumulasi (menghindari garis loncat jauh)
                previous = location
            } else if (d >= 0.5) {
                // Update jarak jika perpindahan >= 0.5 meter
                distance += d
                speedMpsCalculated = speed

                speedSum += speed
                speedCount++
                maxSpeed = maxOf(maxSpeed, speed)

                val prevAlt = previousAltitude ?: currentAltitude
                if (currentAltitude != null && prevAlt != null) {
                    val dz = currentAltitude - prevAlt
                    if (abs(dz) < 10.0) {
                        if (dz > 0) gain += dz else loss += -dz
                    }
                    if (d > 1.0) {
                        gradient = GeoUtils.gradientPercent(d, dz)
                        latestGradient = gradient
                    }
                }
                // HANYA update 'previous' ketika pergerakan valid diterima (d >= 0.5m)
                previous = location
            } else {
                // Jika pergerakan < 0.5m (jitter / sangat pelan), 'previous' TIDAK diubah
                // agar perpindahan kecil dapat terakumulasi pada update berikutnya.
                speedMpsCalculated = if (location.hasSpeed() && location.speed > 0.2f) location.speed.toDouble() else 0.0
            }
        }

        if (currentAltitude != null) {
            previousAltitude = latestAltitude ?: currentAltitude
            latestAltitude = currentAltitude
            maxAltitude = maxOf(maxAltitude ?: currentAltitude, currentAltitude)
        }

        currentSpeedMps = speedMpsCalculated

        val speedToSave = if (location.hasSpeed()) location.speed.toDouble() else null
        val altitudeToSave = currentAltitude

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
    }

    suspend fun finish() {
        if (state == State.IDLE || state == State.FINISHED) return
        stopTimer()
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
