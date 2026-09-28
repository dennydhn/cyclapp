package com.example.cyclapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class LocationTracker(
    context: Context,
    private val onLocation: (Location) -> Unit
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(location)
        }

        // Override eksplisit ini WAJIB untuk mencegah AbstractMethodError di Android 7+ / API 24
        @Deprecated("Deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // Biarkan kosong
        }

        override fun onProviderEnabled(provider: String) {
            // Biarkan kosong
        }

        override fun onProviderDisabled(provider: String) {
            // Biarkan kosong
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            2f,
            listener
        )
    }

    fun stop() {
        manager.removeUpdates(listener)
    }
}