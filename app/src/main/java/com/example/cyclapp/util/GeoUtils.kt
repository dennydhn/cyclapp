package com.example.cyclapp.util

import kotlin.math.*

object GeoUtils{
    private const val EARTH_RADIUS_M= 6371000.0
    //    haversine
    fun distanceMeters(
        lat1: Double, lon1:Double,
        lat2: Double, lon2: Double
    ):Double{
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2-lat1)
        val dl = Math.toRadians(lon2-lon1)

        val a = sin(dp/2).pow(2)+
                cos(p1)*cos(p2)*sin(dl/2).pow(2)
        return 2.0 *EARTH_RADIUS_M*atan2(sqrt(a), sqrt(1.0-a))
    }

    fun kmh(mps: Double):Double=mps*3.6
    //    gradient
    fun gradientPercent(
        horizontalMeters:Double,
        verticalMeters:Double
    ):Double{
        if (horizontalMeters<=0.0) return 0.0
        return(verticalMeters/horizontalMeters)*100.0
    }
}