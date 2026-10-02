package com.example.cyclapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyclapp.ride.RideMetrics
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HeartRateMonitorView(
    metrics: RideMetrics,
    hrStatus: String,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    // Current System Clock State
    var currentTimeString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000L)
        }
    }

    val hr = metrics.heartRate
    val hasHr = hr != null && hr > 0

    // Heart Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (hasHr) 1.22f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (hasHr) (60000 / hr.coerceIn(40, 220)) else 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    // HR Zone Calculation
    val (zoneName, zoneColor, zoneDescription) = getHrZoneInfo(hr)

    val bgColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF4F6F8)
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color(0xFF1A1A1A)
    val subTextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF616161)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Header Card: Jam System & Status Sensor BLE
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Jam Sistem (Clock)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Clock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "WAKTU SAAT INI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = subTextColor
                        )
                        Text(
                            text = currentTimeString.ifEmpty { "--:--:--" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                }

                // Status Koneksi BLE
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (hasHr) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = if (hasHr) "BLE ACTIVE" else hrStatus,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasHr) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Main Center Section: Focus Heart Rate Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heart Animated Icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(zoneColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart Rate",
                        tint = zoneColor,
                        modifier = Modifier
                            .size(56.dp)
                            .scale(pulseScale)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // BPM Value
                Text(
                    text = hr?.toString() ?: "--",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )

                Text(
                    text = "BPM (HEART RATE)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = subTextColor,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // HR Zone Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = zoneColor.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(zoneColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$zoneName ($zoneDescription)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = zoneColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Bottom Durasi Card: Durasi Gowes Real-Time
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Duration",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DURASI PEREKAMAN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = subTextColor
                        )
                        Text(
                            text = formatSeconds(metrics.durationSeconds),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                // Sub-info ringkas Kecepatan & Jarak saat ini
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.1f km/h", metrics.currentSpeedKmh),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f km", metrics.distanceMeters / 1000.0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = subTextColor
                    )
                }
            }
        }
    }
}

private fun getHrZoneInfo(hr: Int?): Triple<String, Color, String> {
    if (hr == null || hr <= 0) return Triple("NO SIGNAL", Color.Gray, "Sambungkan BLE")
    return when {
        hr < 100 -> Triple("RESTING", Color(0xFF00BCD4), "< 100 BPM")
        hr in 100..120 -> Triple("WARM UP", Color(0xFF4CAF50), "100 - 120 BPM")
        hr in 121..140 -> Triple("FAT BURN", Color(0xFF8BC34A), "121 - 140 BPM")
        hr in 141..160 -> Triple("AEROBIC", Color(0xFFFF9800), "141 - 160 BPM")
        hr in 161..180 -> Triple("ANAEROBIC", Color(0xFFFF5722), "161 - 180 BPM")
        else -> Triple("MAX / PEAK", Color(0xFFE91E63), "> 180 BPM")
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
