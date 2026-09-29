package com.example.cyclapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class LocationTracker(
    private val context: Context,
    private val onLocationReceived: (Location) -> Unit
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                onLocationReceived(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        // 1. Ambil lokasi terakhir yang diketahui secara cepat saat aplikasi dinyalakan
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location)
            }
        }

        // 2. Konfigurasi High Accuracy untuk HP Fisik
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L // Interval update setiap 2 detik
        ).apply {
            setMinUpdateIntervalMillis(1000L) // Paling cepat 1 detik
            setMinUpdateDistanceMeters(1f)   // Berpindah minimal 1 meter
            setWaitForAccurateLocation(false) // Mencegah status stuck "Mencari GPS"
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}