package com.example.cyclapp.ride

class AutoPauseController(
    private val pauseSpeedMps: Double = 0.8,     // Kecepatan di bawah 0.8 m/s (~2.8 km/h) dianggap berhenti
    private val resumeSpeedMps: Double = 1.5,    // Kecepatan di atas 1.5 m/s (~5.4 km/h) dianggap mulai bergerak
    private val pauseDelayMs: Long = 8000L        // Menunggu 8 detik sebelum benar-benar memicu Pause
) {
    enum class Action { NONE, PAUSE, RESUME }

    private var stoppedSince: Long? = null

    fun update(speedMps: Double, now: Long, isRecording: Boolean, isPaused: Boolean, isManualPaused: Boolean): Action {
        if (isManualPaused) {
            return Action.NONE // Jika di-pause secara manual, abaikan auto-resume
        }
        if (isRecording && !isPaused) {
            // Cek kondisi Auto-Pause
            if (speedMps < pauseSpeedMps) {
                if (stoppedSince == null) {
                    stoppedSince = now
                }
                if (now - (stoppedSince ?: now) >= pauseDelayMs) {
                    return Action.PAUSE
                }
            } else {
                stoppedSince = null
            }
        } else if (isPaused) {
            // Cek kondisi Auto-Resume
            if (speedMps >= resumeSpeedMps) {
                stoppedSince = null
                return Action.RESUME
            }
        }
        return Action.NONE
    }

    fun reset() {
        stoppedSince = null
    }
}
