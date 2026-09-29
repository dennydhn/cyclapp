package com.example.cyclapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cyclapp.data.db.DatabaseProvider
import com.example.cyclapp.gpx.GpxExporter
import com.example.cyclapp.gpx.GpxImporter
import com.example.cyclapp.gpx.GpxPoint
import com.example.cyclapp.map.ComposeMapProvider
import com.example.cyclapp.ride.RideMetrics
import com.example.cyclapp.ride.RideService
import com.example.cyclapp.ui.RideHistoryScreen
import com.example.cyclapp.ui.components.DashboardOverlay
import com.example.cyclapp.ui.components.NativeMapView
import com.example.cyclapp.ui.theme.CyclappTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    RIDE_CONTROL,
    RIDE_HISTORY
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CyclappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation()
                }
            }
        }
    }
}

@Composable
fun MainAppNavigation() {
    var currentScreen by remember { mutableStateOf(AppScreen.RIDE_CONTROL) }
    val mapProvider = remember { ComposeMapProvider() }

    when (currentScreen) {
        AppScreen.RIDE_CONTROL -> {
            RideControlScreen(
                mapProvider = mapProvider,
                onOpenHistoryClicked = { currentScreen = AppScreen.RIDE_HISTORY }
            )
        }
        AppScreen.RIDE_HISTORY -> {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            RideHistoryScreen(
                onBackClicked = { currentScreen = AppScreen.RIDE_CONTROL },
                onRideSelected = { rideId ->
                    // Muat rute riwayat gowes yang dipilih kembali ke Peta
                    coroutineScope.launch(Dispatchers.IO) {
                        val dao = DatabaseProvider.get(context).rideDao()
                        val points = dao.getTrackPoints(rideId)
                        val gpxPoints = points.map {
                            GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.timestamp)
                        }
                        withContext(Dispatchers.Main) {
                            if (gpxPoints.isNotEmpty()) {
                                mapProvider.drawRecordedTrack(gpxPoints)
                                mapProvider.recenter()
                                Toast.makeText(context, "Memuat rute riwayat ke peta!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Riwayat ini tidak memiliki data lokasi.", Toast.LENGTH_SHORT).show()
                            }
                            currentScreen = AppScreen.RIDE_CONTROL
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun RideControlScreen(
    mapProvider: ComposeMapProvider,
    onOpenHistoryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }

    // State Metrik Gowes Real-Time
    var currentMetrics by remember { mutableStateOf(RideMetrics()) }

    // Launcher untuk Import File GPX
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            coroutineScope.launch(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val importedTrack = inputStream?.use { GpxImporter.parse(context, selectedUri) } ?: emptyList()
                withContext(Dispatchers.Main) {
                    if (importedTrack.isNotEmpty()) {
                        mapProvider.drawImportedRoute(importedTrack)
                        Toast.makeText(context, "Berhasil memuat GPX (${importedTrack.size} titik)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal memuat file GPX!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Launcher untuk Export File GPX
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        uri?.let { destinationUri ->
            coroutineScope.launch(Dispatchers.IO) {
                val dao = DatabaseProvider.get(context).rideDao()
                val rides = dao.getRides()
                if (rides.isNotEmpty()) {
                    val latestRide = rides.first()
                    val points = dao.getTrackPoints(latestRide.id)
                    val gpxContent = GpxExporter.export(latestRide.routeName ?: "Ride", points)
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        outputStream.write(gpxContent.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File GPX berhasil diekspor!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Coroutine Loop untuk update Garis Merah di Peta & Dashboard secara Live
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                val dao = DatabaseProvider.get(context).rideDao()
                val rides = dao.getRides()
                if (rides.isNotEmpty()) {
                    val currentRideId = rides.first().id
                    val points = dao.getTrackPoints(currentRideId)
                    val gpxPoints = points.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.timestamp) }

                    withContext(Dispatchers.Main) {
                        mapProvider.drawRecordedTrack(gpxPoints)

                        if (gpxPoints.isNotEmpty()) {
                            val durationSec = (System.currentTimeMillis() - startTime) / 1000L
                            var totalDist = 0f
                            if (gpxPoints.size >= 2) {
                                for (i in 0 until gpxPoints.size - 1) {
                                    val p1 = gpxPoints[i]
                                    val p2 = gpxPoints[i + 1]
                                    val results = FloatArray(1)
                                    android.location.Location.distanceBetween(
                                        p1.latitude, p1.longitude,
                                        p2.latitude, p2.longitude,
                                        results
                                    )
                                    totalDist += results[0]
                                }
                            }
                            val avgSpeed = if (durationSec > 0) (totalDist / 1000f) / (durationSec / 3600f) else 0f

                            currentMetrics = RideMetrics(
                                durationSeconds = durationSec,
                                distanceMeters = totalDist.toDouble(),
                                currentSpeedKmh = (avgSpeed * 1.1).toDouble(),
                                avgSpeedKmh = avgSpeed.toDouble()
                            )
                        }
                    }
                }
                delay(1000L)
            }
        } else {
            currentMetrics = RideMetrics()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 1. Tampilan Peta Canvas Native
            NativeMapView(
                mapProvider = mapProvider,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Dashboard Metrik Melayang (Aktif saat recording)
            if (isRecording) {
                DashboardOverlay(
                    metrics = currentMetrics,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // 3. Floating Action Button (Recenter)
            if (!mapProvider.isAutoCenterEnabled) {
                SmallFloatingActionButton(
                    onClick = { mapProvider.recenter() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text("📍 Recenter", modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }

        // Panel Kontrol Aplikasi
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Baris 1: Tombol Start & Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val intent = Intent(context, RideService::class.java).apply {
                            action = RideService.ACTION_START
                            putExtra(RideService.EXTRA_ROUTE, "Ride " + System.currentTimeMillis())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        isRecording = true
                        mapProvider.recenter()
                        Toast.makeText(context, "Ride Started!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Start")
                }

                Button(
                    onClick = {
                        val intent = Intent(context, RideService::class.java).apply {
                            action = RideService.ACTION_STOP
                        }
                        context.startService(intent)
                        isRecording = false
                        Toast.makeText(context, "Ride Stopped!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Stop", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Baris 2: Import GPX / Delete GPX & Export GPX
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (mapProvider.importedRoutePoints.isNotEmpty()) "Replace GPX" else "Import GPX")
                }

                if (mapProvider.importedRoutePoints.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            mapProvider.removeImportedRoute()
                            Toast.makeText(context, "Rute GPX dihapus dari peta!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF0F0), contentColor = Color(0xFFD32F2F)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Delete GPX")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val dao = DatabaseProvider.get(context).rideDao()
                                val rides = dao.getRides()
                                withContext(Dispatchers.Main) {
                                    if (rides.isEmpty()) {
                                        Toast.makeText(context, "Belum ada riwayat ride!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        createDocumentLauncher.launch("ride_export_${System.currentTimeMillis()}.gpx")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Export GPX")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Baris 3: Tombol Buka Riwayat Gowes
            OutlinedButton(
                onClick = onOpenHistoryClicked,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("📋 Riwayat Gowes")
            }
        }
    }
}