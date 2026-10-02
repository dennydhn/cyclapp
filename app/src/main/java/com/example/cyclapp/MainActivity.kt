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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.cyclapp.data.db.DatabaseProvider
import com.example.cyclapp.gpx.GpxExporter
import com.example.cyclapp.gpx.GpxImporter
import com.example.cyclapp.gpx.GpxPoint
import com.example.cyclapp.location.LocationTracker
import com.example.cyclapp.map.ComposeMapProvider
import com.example.cyclapp.ride.ActiveRideRepository
import com.example.cyclapp.ride.RideService
import com.example.cyclapp.ui.RideDetailScreen
import com.example.cyclapp.ui.RideHistoryScreen
import com.example.cyclapp.ui.components.DashboardOverlay
import com.example.cyclapp.ui.components.HeartRateMonitorView
import com.example.cyclapp.ui.components.NativeMapView
import com.example.cyclapp.ui.theme.CyclappTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    RIDE_CONTROL,
    RIDE_HISTORY,
    RIDE_DETAIL
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!locationGranted) {
            Toast.makeText(this, "Izin lokasi diperlukan untuk merekam jalur gowes!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            CyclappTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation(
                        isDarkMode = isDarkMode,
                        onDarkModeChanged = { isDarkMode = it }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

@Composable
fun MainAppNavigation(
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.RIDE_CONTROL) }
    var selectedRideId by remember { mutableStateOf<Long?>(null) }
    val mapProvider = remember { ComposeMapProvider() }

    when (currentScreen) {
        AppScreen.RIDE_CONTROL -> {
            RideControlScreen(
                mapProvider = mapProvider,
                isDarkMode = isDarkMode,
                onDarkModeChanged = onDarkModeChanged,
                onOpenHistoryClicked = { currentScreen = AppScreen.RIDE_HISTORY }
            )
        }
        AppScreen.RIDE_HISTORY -> {
            RideHistoryScreen(
                onBackClicked = { currentScreen = AppScreen.RIDE_CONTROL },
                onRideSelected = { rideId ->
                    selectedRideId = rideId
                    currentScreen = AppScreen.RIDE_DETAIL
                }
            )
        }
        AppScreen.RIDE_DETAIL -> {
            selectedRideId?.let { rideId ->
                RideDetailScreen(
                    rideId = rideId,
                    onBackClicked = { currentScreen = AppScreen.RIDE_HISTORY }
                )
            }
        }
    }
}

@Composable
fun CrosshairIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.DarkGray
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2.8f

        // 1. Outer circle
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 2. Center dot
        drawCircle(
            color = tint,
            radius = radius * 0.35f,
            center = center
        )

        // 3. Crosshair ticks (top, bottom, left, right)
        val tickLen = 5.dp.toPx()
        // Top tick
        drawLine(color = tint, start = Offset(center.x, center.y - radius - tickLen), end = Offset(center.x, center.y - radius), strokeWidth = strokeWidth)
        // Bottom tick
        drawLine(color = tint, start = Offset(center.x, center.y + radius), end = Offset(center.x, center.y + radius + tickLen), strokeWidth = strokeWidth)
        // Left tick
        drawLine(color = tint, start = Offset(center.x - radius - tickLen, center.y), end = Offset(center.x - radius, center.y), strokeWidth = strokeWidth)
        // Right tick
        drawLine(color = tint, start = Offset(center.x + radius, center.y), end = Offset(center.x + radius + tickLen, center.y), strokeWidth = strokeWidth)
    }
}

@Composable
fun RideControlScreen(
    mapProvider: ComposeMapProvider,
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onOpenHistoryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGpsDebug by remember { mutableStateOf(true) }
    var isHrMonitoringMode by remember { mutableStateOf(false) }

    val currentMetrics by ActiveRideRepository.metrics.collectAsState()
    val hrStatus by ActiveRideRepository.hrStatus.collectAsState()

    // Start LocationTracker saat idle (belum recording) agar peta & koordinat debug ter-update langsung saat app dibuka
    DisposableEffect(isRecording) {
        if (!isRecording) {
            val idleTracker = LocationTracker(context) { location ->
                mapProvider.showUserLocation(location.latitude, location.longitude)
            }
            idleTracker.start()
            onDispose {
                idleTracker.stop()
            }
        } else {
            onDispose { }
        }
    }

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

    // Coroutine Loop untuk update Garis Merah di Peta secara Live
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
                delay(1000L)
            }
        }
    }

    // Settings Dialog (Latar belakang abu-abu)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFEFEFEF),
            title = {
                Text(
                    "Setelan Aplikasi",
                    color = if (isDarkMode) Color.White else Color.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Dark Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mode Malam (Dark Mode)",
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { onDarkModeChanged(it) }
                        )
                    }

                    // GPS Debug Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Debug GPS (Koordinat)",
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        Switch(
                            checked = showGpsDebug,
                            onCheckedChange = { showGpsDebug = it }
                        )
                    }

                    // HR Monitoring Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mode Monitoring HR (Tanpa Peta)",
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        Switch(
                            checked = isHrMonitoringMode,
                            onCheckedChange = { isHrMonitoringMode = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(
                        "Tutup",
                        color = if (isDarkMode) Color(0xFF64B5F6) else MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }

    // Root Box: Kanvas Peta atau Mode HR Monitoring mengisi layar, Tombol Melayang di atasnya
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Tampilan Utama: Peta atau Mode HR Focus Monitoring
        if (isHrMonitoringMode) {
            HeartRateMonitorView(
                metrics = currentMetrics,
                hrStatus = hrStatus,
                isDarkMode = isDarkMode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 70.dp)
            )
        } else {
            // Peta Canvas Native mengisi SELURUH layar
            NativeMapView(
                mapProvider = mapProvider,
                isDarkMode = isDarkMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Top Bar Overlay (Tombol Menu 3 Garis di pojok kiri atas)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .background(
                            if (isDarkMode) Color(0xFF2C2C2C).copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.85f),
                            RoundedCornerShape(8.dp)
                        )
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = if (isDarkMode) Color.White else Color.DarkGray
                    )
                }

                // Dropdown Menu
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFEFEFEF))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isHrMonitoringMode) "Mode Peta (Map Mode)" else "Mode HR Monitoring",
                                color = if (isDarkMode) Color.White else Color.Black
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            isHrMonitoringMode = !isHrMonitoringMode
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Setelan", color = if (isDarkMode) Color.White else Color.Black) },
                        onClick = {
                            menuExpanded = false
                            showSettingsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import GPX", color = if (isDarkMode) Color(0xFFE0E0E0) else Color.Black) },
                        onClick = {
                            menuExpanded = false
                            openDocumentLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    if (mapProvider.importedRoutePoints.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Hapus Rute GPX di Peta", color = Color(0xFFD32F2F)) },
                            onClick = {
                                menuExpanded = false
                                mapProvider.removeImportedRoute()
                                Toast.makeText(context, "Rute GPX dihapus dari peta!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Riwayat Perekaman", color = if (isDarkMode) Color(0xFFE0E0E0) else Color.Black) },
                        onClick = {
                            menuExpanded = false
                            onOpenHistoryClicked()
                        }
                    )
                }
            }
        }

        // 3. Tombol Recenter (Crosshair Icon) - Hanya muncul di Mode Peta
        if (!isHrMonitoringMode && !mapProvider.isAutoCenterEnabled) {
            IconButton(
                onClick = { mapProvider.recenter() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .background(
                        color = if (isDarkMode) Color(0xFF424242).copy(alpha = 0.85f) else Color(0xFFB0B0B0).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .size(40.dp)
            ) {
                CrosshairIcon(
                    modifier = Modifier.size(24.dp),
                    tint = if (isDarkMode) Color.White else Color.DarkGray
                )
            }
        }

        // 4. Panel Dashboard & Tombol Kontrol MELAYANG di bagian bawah
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dashboard Metrik Data Melayang (Hanya di Mode Peta)
            if (!isHrMonitoringMode) {
                DashboardOverlay(
                    metrics = currentMetrics,
                    isDarkMode = isDarkMode,
                    showGpsDebug = showGpsDebug,
                    fallbackLat = mapProvider.userLat,
                    fallbackLng = mapProvider.userLng,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tombol Kontrol (Start/Pause/Resume & Stop) Melayang di dalam Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xFF242424).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tombol Kiri (Start / Pause / Resume)
                    Button(
                        onClick = {
                            if (!isRecording) {
                                mapProvider.clearRecordedTrack()
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
                                isPaused = false
                                mapProvider.recenter()
                                Toast.makeText(context, "Ride Started!", Toast.LENGTH_SHORT).show()
                            } else {
                                val action = if (isPaused) RideService.ACTION_RESUME else RideService.ACTION_PAUSE
                                val intent = Intent(context, RideService::class.java).apply {
                                    this.action = action
                                }
                                context.startService(intent)
                                isPaused = !isPaused
                                Toast.makeText(context, if (isPaused) "Ride Paused" else "Ride Resumed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isRecording) Color(0xFF2E7D32) // Green for Start
                            else if (isPaused) Color(0xFF388E3C) // Green for Resume
                            else Color(0xFFF57C00) // Orange for Pause
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (!isRecording || isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (!isRecording) "Start" else if (isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Tombol Kanan (Stop - Kotak)
                    Button(
                        onClick = {
                            val intent = Intent(context, RideService::class.java).apply {
                                action = RideService.ACTION_STOP
                            }
                            context.startService(intent)
                            isRecording = false
                            isPaused = false
                            Toast.makeText(context, "Ride Stopped!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Red for Stop
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
