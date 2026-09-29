package com.example.cyclapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.cyclapp.data.db.DatabaseProvider
import com.example.cyclapp.gpx.GpxExporter
import com.example.cyclapp.gpx.GpxImporter
import com.example.cyclapp.gpx.GpxPoint
import com.example.cyclapp.map.ComposeMapProvider
import com.example.cyclapp.ride.RideService
import com.example.cyclapp.ui.components.NativeMapView
import com.example.cyclapp.ui.theme.CyclappTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!locationGranted) {
            Toast.makeText(this, "Izin lokasi diperlukan!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        enableEdgeToEdge()

        setContent {
            CyclappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RideControlScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun RideControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }

    // MapProvider Native Compose Canvas
    val mapProvider = remember { ComposeMapProvider() }

    // Launcher SAF untuk Membuka / Import File GPX (Rute Biru)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            coroutineScope.launch(Dispatchers.IO) {
                val importedPoints = GpxImporter.parse(context, selectedUri)
                withContext(Dispatchers.Main) {
                    if (importedPoints.isNotEmpty()) {
                        // Cukup gambar rute GPX tanpa memindahkan lokasi pengguna
                        mapProvider.drawImportedRoute(importedPoints)

                        Toast.makeText(context, "Berhasil memuat rute GPX (${importedPoints.size} titik)!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal memuat file GPX!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Launcher SAF untuk Menyimpan / Export GPX
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        uri?.let { destinationUri ->
            coroutineScope.launch(Dispatchers.IO) {
                val dao = DatabaseProvider.get(context).rideDao()
                val rides = dao.getRides()
                if (rides.isNotEmpty()) {
                    val lastRide = rides.first()
                    val points = dao.getTrackPoints(lastRide.id)
                    val gpxData = GpxExporter.export("Ride_${lastRide.id}", points)

                    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                        output.write(gpxData.toByteArray())
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File GPX berhasil disimpan!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Coroutine Loop untuk membaca Room DB & memperbarui garis rekaman gowes secara real-time
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                val dao = DatabaseProvider.get(context).rideDao()
                val rides = dao.getRides()
                if (rides.isNotEmpty()) {
                    val currentRideId = rides.first().id
                    val points = dao.getTrackPoints(currentRideId)
                    val gpxPoints = points.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.timestamp) }

                    withContext(Dispatchers.Main) {
                        mapProvider.drawRecordedTrack(gpxPoints)
                    }
                }
                delay(1500L) // Refresh setiap 1.5 detik
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Tampilan Peta Native Compose (Memuat NativeMapView Canvas)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            NativeMapView(
                mapProvider = mapProvider,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Panel Kontrol (Tombol Start, Stop, Import GPX, Export GPX)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tombol Start
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
                        Toast.makeText(context, "Ride Started!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Start")
                }

                // Tombol Stop
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tombol Import GPX
                OutlinedButton(
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Import GPX Route")
                }

                // Tombol Export GPX
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
    }
}