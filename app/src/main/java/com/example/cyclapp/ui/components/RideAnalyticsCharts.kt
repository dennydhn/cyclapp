package com.example.cyclapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyclapp.data.db.TrackPointEntity
import java.util.Locale

@Composable
fun SpeedAnalyticsChart(
    points: List<TrackPointEntity>,
    modifier: Modifier = Modifier
) {
    val speedData = points.map { (it.speedMps ?: 0.0) * 3.6 } // Convert m/s ke km/h
    val maxSpeed = speedData.maxOrNull()?.coerceAtLeast(10.0) ?: 10.0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GRAFIK KECEPATAN (KM/H)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFF8F9FA))
            ) {
                if (speedData.size < 2) return@Canvas

                val width = size.width
                val height = size.height
                val stepX = width / (speedData.size - 1)

                val path = Path()
                speedData.forEachIndexed { index, speed ->
                    val x = index * stepX
                    val y = height - ((speed / maxSpeed) * height).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = Color(0xFF1976D2),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(Locale.US, "Max Speed: %.1f km/h", maxSpeed),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1976D2)
            )
        }
    }
}

@Composable
fun ElevationAnalyticsChart(
    points: List<TrackPointEntity>,
    modifier: Modifier = Modifier
) {
    val elevationData = points.mapNotNull { it.altitudeMeters }
    val minEle = elevationData.minOrNull() ?: 0.0
    val maxEle = elevationData.maxOrNull()?.coerceAtLeast(minEle + 10.0) ?: 100.0
    val rangeEle = (maxEle - minEle).coerceAtLeast(1.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PROFIL ELEVASI / KETINGGIAN (M)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFF8F9FA))
            ) {
                if (elevationData.size < 2) return@Canvas

                val width = size.width
                val height = size.height
                val stepX = width / (elevationData.size - 1)

                val path = Path()
                elevationData.forEachIndexed { index, ele ->
                    val x = index * stepX
                    val normalizedY = ((ele - minEle) / rangeEle).toFloat()
                    val y = height - (normalizedY * (height - 20f)) - 10f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = Color(0xFF388E3C),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Min: %.0f m", minEle),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = String.format(Locale.US, "Max: %.0f m", maxEle),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF388E3C)
                )
            }
        }
    }
}

@Composable
fun HeartRateAnalyticsChart(
    points: List<TrackPointEntity>,
    modifier: Modifier = Modifier
) {
    val hrData = points.mapNotNull { it.heartRate }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GRAFIK DENYUT JANTUNG (BPM)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (hrData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(0xFFF8F9FA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada data denyut jantung (Sensor HR tidak terhubung)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                val minHr = hrData.minOrNull() ?: 60
                val maxHr = hrData.maxOrNull()?.coerceAtLeast(minHr + 20) ?: 180
                val rangeHr = (maxHr - minHr).coerceAtLeast(1)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFFF8F9FA))
                ) {
                    if (hrData.size < 2) return@Canvas

                    val width = size.width
                    val height = size.height
                    val stepX = width / (hrData.size - 1)

                    val path = Path()
                    hrData.forEachIndexed { index, hr ->
                        val x = index * stepX
                        val normalizedY = ((hr - minHr).toFloat() / rangeHr.toFloat())
                        val y = height - (normalizedY * (height - 20f)) - 10f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFFD32F2F),
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "Min: %d bpm", minErr(minHr)),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = String.format(Locale.US, "Max: %d bpm", maxHr),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}

private fun minErr(v: Int) = v
