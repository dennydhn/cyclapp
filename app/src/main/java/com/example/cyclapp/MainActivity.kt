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
import com.example.cyclapp.ride.RideService
import com.example.cyclapp.ui.theme.CyclappTheme
import kotlinx.coroutines.Dispatchers
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

    // Storage Access Framework Launcher untuk simpan file GPX
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Cycle Tracker Control", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Start
        Button(
            onClick = {
                val intent = Intent(context, RideService::class.java).apply {
                    action = RideService.ACTION_START
                    putExtra(RideService.EXTRA_ROUTE, "Uji Coba Ride")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Toast.makeText(context, "Ride Started!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Start Ride")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tombol Stop
        Button(
            onClick = {
                val intent = Intent(context, RideService::class.java).apply {
                    action = RideService.ACTION_STOP
                }
                context.startService(intent)
                Toast.makeText(context, "Ride Stopped!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Stop Ride", color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Export GPX (SAF Launcher)
        OutlinedButton(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val dao = DatabaseProvider.get(context).rideDao()
                    val rides = dao.getRides()
                    withContext(Dispatchers.Main) {
                        if (rides.isEmpty()) {
                            Toast.makeText(context, "Belum ada riwayat ride!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Memicu dialog penyimpan file Android
                            createDocumentLauncher.launch("ride_export_${System.currentTimeMillis()}.gpx")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Export Last Ride to GPX")
        }
    }
}