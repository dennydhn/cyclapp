package com.example.cyclapp.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cyclapp.gpx.GpxPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class ComposeMapProvider : MapProvider {

    var userLat by mutableDoubleStateOf(-7.7956)
    var userLng by mutableDoubleStateOf(110.3695)
    var userHeading by mutableFloatStateOf(0f)

    var recordedTrackPoints by mutableStateOf<List<GpxPoint>>(emptyList())
    var importedRoutePoints by mutableStateOf<List<GpxPoint>>(emptyList())

    override fun showUserLocation(latitude: Double, longitude: Double) {
        if (userLat != latitude || userLng != longitude) {
            userHeading = calculateBearing(userLat, userLng, latitude, longitude)
            userLat = latitude
            userLng = longitude
        }
    }

    override fun drawRecordedTrack(points: List<GpxPoint>) {
        recordedTrackPoints = points
        if (points.isNotEmpty()) {
            val lastPoint = points.last()

            if (points.size >= 2) {
                val prevPoint = points[points.size - 2]
                userHeading = calculateBearing(
                    prevPoint.latitude, prevPoint.longitude,
                    lastPoint.latitude, lastPoint.longitude
                )
            }

            // Perbarui lokasi pengguna sesuai koordinat GPS real-time
            userLat = lastPoint.latitude
            userLng = lastPoint.longitude
        }
    }

    override fun drawImportedRoute(points: List<GpxPoint>) {
        // HANYA simpan titik rute GPX untuk digambar sebagai garis biru,
        // TIDAK MENUBAH userLat dan userLng pengguna.
        importedRoutePoints = points
    }

    override fun clearRoute() {
        recordedTrackPoints = emptyList()
        importedRoutePoints = emptyList()
    }

    override fun zoomToRoute(points: List<GpxPoint>) {
        // Fungsi ini sengaja dikosongkan/dibiarkan tanpa mengubah userLat/userLng
        // agar anak panah tetap berada murni pada koordinat GPS pengguna.
    }

    private fun calculateBearing(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double
    ): Float {
        val lat1 = Math.toRadians(startLat)
        val lat2 = Math.toRadians(endLat)
        val dLng = Math.toRadians(endLng - startLng)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        if (bearing < 0) {
            bearing += 360f
        }
        return bearing
    }
}