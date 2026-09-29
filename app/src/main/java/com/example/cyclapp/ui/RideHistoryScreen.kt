package com.example.cyclapp.ui



import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    onBackClicked: () -> Unit,
    onRideSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var ridesList by remember { mutableStateOf<List<RideEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Memuat daftar riwayat dari Room DB
    fun loadRides() {
        coroutineScope.launch(Dispatchers.IO) {
            val dao = DatabaseProvider.get(context).rideDao()
            val list = dao.getRides()
            withContext(Dispatchers.Main) {
                ridesList = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRides()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Gowes") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (ridesList.isEmpty()) {
                Text(
                    text = "Belum ada riwayat gowes tersimpan.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ridesList) { ride ->
                        RideItemCard(
                            ride = ride,
                            onClick = { onRideSelected(ride.id) },
                            onDelete = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val dao = DatabaseProvider.get(context).rideDao()
                                    dao.deleteRide(ride.id)
                                    dao.deleteTrackPoints(ride.id)
                                    loadRides()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RideItemCard(
    ride: RideEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(ride.startTime))
    val distanceKm = ride.distanceMeters / 1000.0
    val durationSec = (ride.elapsedTimeMs ?: 0L) / 1000L
    val avgSpeedKmh = ride.avgSpeedMps * 3.6

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ride.routeName ?: "Un-named Ride",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateString,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = String.format(Locale.US, "%.2f km", distanceKm),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f km/h", avgSpeedKmh),
                        fontWeight = FontWeight.Normal,
                        color = Color.DarkGray
                    )
                    Text(
                        text = formatSeconds(durationSec),
                        fontWeight = FontWeight.Normal,
                        color = Color.DarkGray
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus Riwayat",
                    tint = Color(0xFFD32F2F)
                )
            }
        }
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