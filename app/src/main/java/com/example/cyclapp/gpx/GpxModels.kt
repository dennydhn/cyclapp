package com.example.cyclapp.gpx

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timeMillis: Long? = null
)

data class GpxTrack(
    val name: String?,
    val points: List<GpxPoint>
)