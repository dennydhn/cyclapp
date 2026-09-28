package com.example.cyclapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager

class LocationTracker(
    context: Context,
    private val onLocation: (Location)->Unit
){
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val listener = LocationListener{location->
        onLocation(location)
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
    fun stop(){
        manager.removeUpdates(listener)
    }
}