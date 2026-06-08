package com.smartleaf.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Removed Vico imports
import com.smartleaf.app.R
import com.smartleaf.app.ui.AuthState
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.theme.CardBackground
import com.smartleaf.app.ui.theme.VibrantGreen
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel, 
    onNavigateToCapture: () -> Unit,
    onNavigateToScanDetail: (Long) -> Unit,
    onNavigateToYieldAnalytics: () -> Unit
) {
    val stats by viewModel.dashboardStats.collectAsState(initial = emptyList())
    val authState by viewModel.authState.collectAsState()
    val userName = if (authState is AuthState.Success) {
        (authState as AuthState.Success).user.fullName.split(" ").firstOrNull() ?: "User"
    } else {
        "User"
    }
    
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val hasHighGrade = stats.any { it.qualityGrade == "Grade A" || it.qualityGrade == "Grade B" }
    
    var currentTab by remember { mutableStateOf("Home") }
    
    val locationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> 
        // We just request it, the map handles the rest via osmdroid defaults mostly.
    }
    
    LaunchedEffect(Unit) {
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    val total = stats.size.coerceAtLeast(1)
    val matureCount = stats.count { it.maturityPhase == "Mature" }
    val pseudoCount = stats.count { it.maturityPhase == "Pseudomature" }
    val immatureCount = stats.count { it.maturityPhase.contains("Immature", true) || it.maturityPhase.contains("Hyper", true) }
    
    val maturePct = (matureCount.toFloat() / total) * 100f
    val pseudoPct = (pseudoCount.toFloat() / total) * 100f
    val immaturePct = (immatureCount.toFloat() / total) * 100f
    
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy | hh:mm a", Locale.getDefault())
        while (true) {
            currentTimeString = dateFormat.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = Color.White,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(72.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavIcon(Icons.Default.Home, "Home", currentTab == "Home") { currentTab = "Home" }
                    BottomNavIcon(Icons.Default.BarChart, "Stats", currentTab == "Stats") { currentTab = "Stats" }
                    Spacer(modifier = Modifier.width(48.dp)) // Space for FAB
                    BottomNavIcon(Icons.Default.History, "History", currentTab == "History") { currentTab = "History" }
                    BottomNavIcon(Icons.Default.Settings, "Settings", currentTab == "Settings") { currentTab = "Settings" }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCapture,
                containerColor = VibrantGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .offset(y = 36.dp) // Offset to overlap bottom bar perfectly
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = "Scan",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                "Home" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // Header Section
                        item {
                            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = VibrantGreen,
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFFFFD54F), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        userName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color(0xFF8D6E63)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Hello, $userName", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Welcome back to your farm", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                            Box {
                                IconButton(onClick = { /* Notifications */ }) {
                                    Icon(Icons.Default.Notifications, "Notifications", tint = Color.White)
                                }
                                if (notificationsEnabled && hasHighGrade) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color.Red, CircleShape)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-8).dp, y = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = "Time", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentTimeString,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp)) // Space for floating cards
                    }
                }
            }

            // Top Cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .offset(y = (-20).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = VibrantGreen)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = VibrantGreen.copy(alpha=0.1f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("+12%", color = VibrantGreen, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "${stats.size}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "Leaves Scanned", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.WaterDrop, null, tint = Color(0xFF42A5F5))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "82%", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "Avg. Moisture", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // Yield Analytics
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToYieldAnalytics() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Yield Analytics", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("View Detail", fontSize = 12.sp, color = VibrantGreen, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // Simple Custom Donut representation since Vico standard is bar/line
                                Canvas(modifier = Modifier.size(100.dp)) {
                                    val strokeWidth = 15.dp.toPx()
                                    val matureAngle = (maturePct / 100f) * 360f
                                    val pseudoAngle = (pseudoPct / 100f) * 360f
                                    val immatureAngle = (immaturePct / 100f) * 360f

                                    drawArc(
                                        color = VibrantGreen,
                                        startAngle = -90f,
                                        sweepAngle = matureAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                    )
                                    drawArc(
                                        color = Color(0xFFFFCA28),
                                        startAngle = -90f + matureAngle,
                                        sweepAngle = pseudoAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                    )
                                    drawArc(
                                        color = Color(0xFFEF5350),
                                        startAngle = -90f + matureAngle + pseudoAngle,
                                        sweepAngle = immatureAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${"%.0f".format(maturePct)}%", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("MATURE", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                LegendItem("Mature", "${"%.0f".format(maturePct)}%", VibrantGreen)
                                Spacer(modifier = Modifier.height(8.dp))
                                LegendItem("Pseudomature", "${"%.0f".format(pseudoPct)}%", Color(0xFFFFCA28))
                                Spacer(modifier = Modifier.height(8.dp))
                                LegendItem("Immature", "${"%.0f".format(immaturePct)}%", Color(0xFFEF5350))
                            }
                        }
                    }
                }
            }

            // Spatial Maturity Map
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Spatial Maturity Map", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val mapView = remember {
                                val config = org.osmdroid.config.Configuration.getInstance()
                                config.userAgentValue = context.packageName
                                config.cacheMapTileCount = 12.toShort()
                                config.cacheMapTileOvershoot = 12.toShort()
                                config.tileFileSystemCacheMaxBytes = 500L * 1024 * 1024 // 500MB
                                
                                org.osmdroid.views.MapView(context).apply {
                                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    val myLocation = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
                                        org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(context), this
                                    )
                                    myLocation.enableMyLocation()
                                    overlays.add(myLocation)
                                    
                                    controller.setZoom(18.0)
                                    val centerLat = stats.firstOrNull { it.latitude != 0.0 }?.latitude ?: -6.2088
                                    val centerLon = stats.firstOrNull { it.longitude != 0.0 }?.longitude ?: 106.8456
                                    controller.setCenter(org.osmdroid.util.GeoPoint(centerLat, centerLon))
                                }
                            }
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { mapView },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    val overlaysToKeep = view.overlays.filterIsInstance<org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay>()
                                    view.overlays.clear()
                                    view.overlays.addAll(overlaysToKeep)
                                    stats.forEach { stat ->
                                        if (stat.latitude != 0.0 && stat.longitude != 0.0) {
                                            val marker = org.osmdroid.views.overlay.Marker(view)
                                            marker.position = org.osmdroid.util.GeoPoint(stat.latitude, stat.longitude)
                                            marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                                            
                                            // Tint default marker based on maturity
                                            val defaultIcon = androidx.core.content.ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()
                                            val colorFilter = if (stat.maturityPhase.contains("Pseudo", true)) {
                                                android.graphics.Color.rgb(255, 202, 40) // Yellow
                                            } else if (stat.maturityPhase.contains("Mature", true)) {
                                                android.graphics.Color.rgb(0, 200, 83) // Green
                                            } else {
                                                android.graphics.Color.rgb(239, 83, 80) // Red
                                            }
                                            defaultIcon?.setTint(colorFilter)
                                            marker.icon = defaultIcon
                                            
                                            view.overlays.add(marker)
                                        }
                                    }
                                    view.invalidate()
                                }
                            )
                        }
                    }
                }
            }
            } // End of LazyColumn
                }
                "Stats" -> {
                    StatsScreen(stats = stats)
                }
                "History" -> {
                    HistoryScreen(viewModel = viewModel, onNavigateToDetail = onNavigateToScanDetail)
                }
                "Settings" -> {
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, color = Color.DarkGray)
        }
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BottomNavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) VibrantGreen else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isSelected) VibrantGreen else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
