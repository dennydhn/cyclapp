package com.example.cyclapp.map

import com.example.cyclapp.gpx.GpxPoint

interface MapProvider {
    fun showUserLocation(latitude: Double, longitude: Double)
    fun drawRecordedTrack(points: List<GpxPoint>)
    fun drawImportedRoute(points: List<GpxPoint>)
    fun clearRoute()
    fun zoomToRoute(points: List<GpxPoint>)
}