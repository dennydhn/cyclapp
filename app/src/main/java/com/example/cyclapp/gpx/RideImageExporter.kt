package com.example.cyclapp.gpx

import android.graphics.*
import com.example.cyclapp.data.db.RideEntity
import com.example.cyclapp.data.db.TrackPointEntity
import java.util.*
import kotlin.math.min

object RideImageExporter {
    fun generateRideImage(ride: RideEntity, points: List<TrackPointEntity>): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Transparent Background
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 2. Track Drawing Area (Bounding Box) in upper portion
        val trackAreaTop = 100f
        val trackAreaBottom = 820f
        val trackAreaLeft = 100f
        val trackAreaRight = (width - 100).toFloat()
        val trackAreaWidth = trackAreaRight - trackAreaLeft
        val trackAreaHeight = trackAreaBottom - trackAreaTop

        if (points.size >= 2) {
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }
            val maxLon = points.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.00001)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.00001)

            val scaleX = trackAreaWidth / lonSpan
            val scaleY = trackAreaHeight / latSpan
            val scale = min(scaleX, scaleY)

            val renderedWidth = lonSpan * scale
            val renderedHeight = latSpan * scale
            val offsetX = trackAreaLeft + (trackAreaWidth - renderedWidth) / 2f
            val offsetY = trackAreaTop + (trackAreaHeight - renderedHeight) / 2f

            val trackPath = Path()
            points.forEachIndexed { index, pt ->
                val x = (pt.longitude - minLon) * scale + offsetX
                val y = trackAreaBottom - ((pt.latitude - minLat) * scale + (offsetY - trackAreaTop))

                if (index == 0) {
                    trackPath.moveTo(x.toFloat(), y.toFloat())
                } else {
                    trackPath.lineTo(x.toFloat(), y.toFloat())
                }
            }

            // Track shadow / glow
            val shadowPaint = Paint().apply {
                color = Color.parseColor("#44FC4C02")
                style = Paint.Style.STROKE
                strokeWidth = 24f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(trackPath, shadowPaint)

            // Track line (Vibrant Strava Orange #FC4C02)
            val trackPaint = Paint().apply {
                color = Color.parseColor("#FC4C02")
                style = Paint.Style.STROKE
                strokeWidth = 10f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(trackPath, trackPaint)
        } else {
            val noTrackPaint = Paint().apply {
                color = Color.parseColor("#AAAAAA")
                textSize = 36f
                isAntiAlias = true
            }
            canvas.drawText("Tidak ada data jalur rute", 100f, 450f, noTrackPaint)
        }

        // 3. Branding ("CYCLETRACKER") centered below track
        val brandPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 3f, Color.BLACK)
        }
        canvas.drawText("CYCLETRACKER", width / 2f, 920f, brandPaint)

        // 4. Stats Section (3 Columns centered: Distance, Elev Gain, Time)
        val labelPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        val valuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 3f, Color.BLACK)
        }

        val distanceKm = ride.distanceMeters / 1000.0
        val distanceStr = String.format(Locale.US, "%.2f km", distanceKm)
        val elevGainStr = String.format(Locale.US, "%.0f m", ride.elevationGainMeters)
        val durationSec = (ride.elapsedTimeMs ?: 0L) / 1000L
        val durationStr = formatDurationCompact(durationSec)

        val col1X = width * 0.25f
        val col2X = width * 0.50f
        val col3X = width * 0.75f

        val statYLabel = 1020f
        val statYValue = 1080f

        // Column 1: Distance
        canvas.drawText("Distance", col1X, statYLabel, labelPaint)
        canvas.drawText(distanceStr, col1X, statYValue, valuePaint)

        // Column 2: Elev Gain
        canvas.drawText("Elev Gain", col2X, statYLabel, labelPaint)
        canvas.drawText(elevGainStr, col2X, statYValue, valuePaint)

        // Column 3: Time
        canvas.drawText("Time", col3X, statYLabel, labelPaint)
        canvas.drawText(durationStr, col3X, statYValue, valuePaint)

        // 5. Bicycle Icon at bottom center
        val bikePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        val bikeCenterX = width / 2f
        val bikeCenterY = 1180f
        canvas.drawCircle(bikeCenterX - 30f, bikeCenterY + 10f, 16f, bikePaint)
        canvas.drawCircle(bikeCenterX + 30f, bikeCenterY + 10f, 16f, bikePaint)
        val bikePath = Path().apply {
            moveTo(bikeCenterX - 30f, bikeCenterY + 10f)
            lineTo(bikeCenterX - 10f, bikeCenterY - 10f)
            lineTo(bikeCenterX + 15f, bikeCenterY - 10f)
            lineTo(bikeCenterX + 30f, bikeCenterY + 10f)
            moveTo(bikeCenterX - 10f, bikeCenterY - 10f)
            lineTo(bikeCenterX, bikeCenterY - 25f)
            moveTo(bikeCenterX - 20f, bikeCenterY - 10f)
            lineTo(bikeCenterX - 5f, bikeCenterY - 10f)
        }
        canvas.drawPath(bikePath, bikePaint)

        return bitmap
    }

    private fun formatDurationCompact(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            if (mins > 0) "${hrs}h ${mins}m" else "${hrs}h"
        } else if (mins > 0) {
            "${mins}m"
        } else {
            "${secs}s"
        }
    }
}
