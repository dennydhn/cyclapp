package com.example.cyclapp.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveRideRepository {
    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    fun updateMetrics(metrics: RideMetrics) {
        _metrics.value = metrics
    }
}
