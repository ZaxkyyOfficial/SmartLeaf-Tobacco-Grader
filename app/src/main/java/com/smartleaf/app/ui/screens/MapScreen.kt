package com.smartleaf.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartleaf.app.data.local.SmartLeafResult
import com.smartleaf.app.ui.theme.VibrantGreen
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(stats: List<SmartLeafResult>) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            val mapController = controller
            mapController.setZoom(12.0)
            
            val centerLat = stats.firstOrNull { it.latitude != 0.0 }?.latitude ?: -6.2088
            val centerLon = stats.firstOrNull { it.longitude != 0.0 }?.longitude ?: 106.8456
            val startPoint = GeoPoint(centerLat, centerLon)
            mapController.setCenter(startPoint)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // App Bar / Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibrantGreen, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .padding(24.dp)
        ) {
            Column {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Spatial Map", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("View your scan locations", fontSize = 14.sp, color = Color.White.copy(alpha=0.8f))
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.overlays.clear()
                        stats.forEach { stat ->
                            if (stat.latitude != 0.0 && stat.longitude != 0.0) {
                                val marker = Marker(view)
                                marker.position = GeoPoint(stat.latitude, stat.longitude)
                                marker.title = "Batch #${stat.id} - ${stat.maturityPhase}"
                                marker.snippet = "Grade: ${stat.qualityGrade}\nMoisture: ${"%.1f".format(stat.moisture)}%"
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
