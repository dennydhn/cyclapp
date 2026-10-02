package com.example.cyclapp.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveRideRepository {
    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    private val _hrStatus = MutableStateFlow("Siap Terhubung")
    val hrStatus: StateFlow<String> = _hrStatus.asStateFlow()

    fun updateMetrics(metrics: RideMetrics) {
        _metrics.value = metrics
    }

    fun updateHrStatus(status: String) {
        _hrStatus.value = status
    }
}
