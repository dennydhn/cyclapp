package com.example.cyclapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyclapp.ride.RideMetrics
import java.util.*

@Composable
fun DashboardOverlay(
    metrics: RideMetrics,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false
) {
    val containerColor = if (isDarkMode) Color(0xFF242424).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
    val textColor = if (isDarkMode) Color.White else Color(0xFF212121)
    val labelColor = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray
    val dividerColor = if (isDarkMode) Color(0xFF3A3A3A) else Color(0xFFE0E0E0)
    val speedColor = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF1976D2)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Indikator Status Auto Paused (jika aktif)
            if (metrics.isAutoPaused) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "⏸ AUTO PAUSED",
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Baris Utama: Kecepatan Saat Ini (SPEED)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "SPEED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", metrics.currentSpeedKmh),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = speedColor
                    )
                }
                Text(
                    text = "km/h",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Grid Metrik Tambahan
            // Baris 1: Distance, Duration, Avg Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "DISTANCE",
                    value = String.format(Locale.US, "%.2f km", metrics.distanceMeters / 1000.0),
                    textColor = textColor,
                    labelColor = labelColor
                )

                MetricItem(
                    label = "DURATION",
                    value = formatSeconds(metrics.durationSeconds),
                    textColor = textColor,
                    labelColor = labelColor
                )

                MetricItem(
                    label = "AVG SPEED",
                    value = String.format(Locale.US, "%.1f km/h", metrics.avgSpeedKmh),
                    textColor = textColor,
                    labelColor = labelColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Baris 2: Altitude, Gradient, Heart Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "ALTITUDE",
                    value = metrics.altitudeMeters?.let { String.format(Locale.US, "%.0f m", it) } ?: "-- m",
                    textColor = textColor,
                    labelColor = labelColor
                )

                MetricItem(
                    label = "GRADIENT",
                    value = metrics.gradientPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--%",
                    textColor = textColor,
                    labelColor = labelColor
                )

                MetricItem(
                    label = "HEART RATE",
                    value = metrics.heartRate?.let { "$it bpm" } ?: "-- bpm",
                    textColor = textColor,
                    labelColor = labelColor
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, textColor: Color, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private fun formatSeconds(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
