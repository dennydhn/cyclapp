package com.example.cyclapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.example.cyclapp.map.ComposeMapProvider

@Composable
fun NativeMapView(
    mapProvider: ComposeMapProvider,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 1. Gesture Zoom (Pinch)
                    val newScale = mapProvider.zoomScale * zoom
                    mapProvider.zoomScale = newScale.coerceIn(10000f, 300000f) // Batas Zoom Min/Max

                    // 2. Gesture Pan/Drag (Seret Peta)
                    mapProvider.panOffsetX += pan.x
                    mapProvider.panOffsetY += pan.y

                    // Matikan Auto-Center saat pengguna menggeser peta secara manual
                    if (pan.x != 0f || pan.y != 0f) {
                        mapProvider.isAutoCenterEnabled = false
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        val centerLat = mapProvider.userLat
        val centerLng = mapProvider.userLng
        val scale = mapProvider.zoomScale
        val panX = mapProvider.panOffsetX
        val panY = mapProvider.panOffsetY

        fun latLngToOffset(lat: Double, lng: Double): Offset {
            val x = width / 2f + ((lng - centerLng) * scale).toFloat() + panX
            val y = height / 2f - ((lat - centerLat) * scale).toFloat() + panY
            return Offset(x, y)
        }

        // 1. Gambar Grid Latar Belakang
        val step = 80f
        var x = 0f
        while (x < width) {
            drawLine(
                color = Color(0xFFD0D7DE),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += step
        }
        var y = 0f
        while (y < height) {
            drawLine(
                color = Color(0xFFD0D7DE),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += step
        }

        // 2. Gambar Rute Impor GPX (Garis Biru)
        val importedPts = mapProvider.importedRoutePoints
        if (importedPts.size > 1) {
            val path = Path()
            importedPts.forEachIndexed { index, pt ->
                val offset = latLngToOffset(pt.latitude, pt.longitude)
                if (index == 0) path.moveTo(offset.x, offset.y)
                else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = Color(0xFF1565C0),
                style = Stroke(
                    width = 12f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 3. Gambar Rekaman Gowes Real-Time (Garis Merah)
        val recordedPts = mapProvider.recordedTrackPoints
        if (recordedPts.size > 1) {
            val path = Path()
            recordedPts.forEachIndexed { index, pt ->
                val offset = latLngToOffset(pt.latitude, pt.longitude)
                if (index == 0) path.moveTo(offset.x, offset.y)
                else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = Color(0xFFD32F2F),
                style = Stroke(
                    width = 14f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 4. Gambar Penanda Lokasi Pengguna (Panah Arah)
        val userOffset = latLngToOffset(centerLat, centerLng)

        rotate(degrees = mapProvider.userHeading, pivot = userOffset) {
            val arrowPath = Path().apply {
                moveTo(userOffset.x, userOffset.y - 35f)
                lineTo(userOffset.x + 22f, userOffset.y + 25f)
                lineTo(userOffset.x, userOffset.y + 12f)
                lineTo(userOffset.x - 22f, userOffset.y + 25f)
                close()
            }

            drawPath(
                path = arrowPath,
                color = Color.White,
                style = Stroke(width = 6f)
            )
            drawPath(
                path = arrowPath,
                color = Color(0xFF1976D2)
            )
        }
    }
}