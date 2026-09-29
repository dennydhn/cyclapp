package com.example.cyclapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyclapp.data.db.DatabaseProvider
import com.example.cyclapp.data.db.RideEntity
import com.example.cyclapp.data.db.TrackPointEntity
import com.example.cyclapp.ui.components.ElevationAnalyticsChart
import com.example.cyclapp.ui.components.SpeedAnalyticsChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    rideId: Long,
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var ride by remember { mutableStateOf<RideEntity?>(null) }
    var trackPoints by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(rideId) {
        coroutineScope.launch(Dispatchers.IO) {
            val dao = DatabaseProvider.get(context).rideDao()
            val rideData = dao.getRide(rideId)
            val points = dao.getTrackPoints(rideId)
            withContext(Dispatchers.Main) {
                ride = rideData
                trackPoints = points
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ride?.routeName ?: "Detail Gowes") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (ride == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Data riwayat tidak ditemukan.")
            }
        } else {
            val currentRide = ride!!
            val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.getDefault()) }
            val dateStr = dateFormat.format(Date(currentRide.startTime))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Info Tanggal
                Text(
                    text = dateStr,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                // Grid Ringkasan Statistik Utama
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox(
                                label = "JARAK TOTAL",
                                value = String.format(Locale.US, "%.2f km", currentRide.distanceMeters / 1000.0)
                            )
                            StatBox(
                                label = "DURASI",
                                value = formatSeconds((currentRide.elapsedTimeMs ?: 0L) / 1000L)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox(
                                label = "KECEPATAN RATA-RATA",
                                value = String.format(Locale.US, "%.1f km/h", currentRide.avgSpeedMps * 3.6)
                            )
                            StatBox(
                                label = "KECEPATAN MAX",
                                value = String.format(Locale.US, "%.1f km/h", currentRide.maxSpeedMps * 3.6)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox(
                                label = "ELEVATION GAIN",
                                value = String.format(Locale.US, "+%.0f m", currentRide.elevationGainMeters)
                            )
                            StatBox(
                                label = "ALTITUDE MAX",
                                value = currentRide.maxAltitudeMeters?.let {
                                    String.format(Locale.US, "%.0f m", it)
                                } ?: "-- m"
                            )
                        }
                    }
                }

                // Grafik Kecepatan
                if (trackPoints.isNotEmpty()) {
                    SpeedAnalyticsChart(points = trackPoints)
                    ElevationAnalyticsChart(points = trackPoints)
                } else {
                    Text(
                        text = "Tidak ada titik sampel koordinat untuk grafik.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
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