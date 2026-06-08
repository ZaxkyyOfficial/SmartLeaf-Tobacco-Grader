package com.smartleaf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smartleaf.app.R
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.theme.VibrantGreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScanDetailScreen(
    recordId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.dashboardStats.collectAsState(initial = emptyList())
    val result = stats.find { it.id == recordId }

    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Record not found", color = Color.Gray)
        }
        return
    }

    val displayName = if (result.scanName.isNotEmpty()) result.scanName else "Batch #${result.id}"

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // --- HEADER SECTION (IMAGE) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = result.imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.5f,
                placeholder = painterResource(id = R.drawable.ic_leaf_logo),
                error = painterResource(id = R.drawable.ic_leaf_logo)
            )

            // Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(Color.Black.copy(alpha=0.4f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    displayName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f)
                    )
                )
                Spacer(modifier = Modifier.size(48.dp)) // balance
            }
        }

        // --- CONTENT SECTION (SCROLLABLE) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 280.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Result Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.LightGray, spotColor = Color.LightGray),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(result.maturityPhase, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(result.qualityGrade, fontSize = 16.sp, color = VibrantGreen)

                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Divider(color = Color(0xFFE2E8F0))
                    Spacer(Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("CONFIDENCE", "${"%.1f".format(result.confidence)}%")
                        StatItem("COLOR", result.colorCode.ifEmpty { "N/A" })
                        StatItem("MOISTURE", "${"%.1f".format(result.moisture)}%")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Harvest Schedule Card
            HarvestScheduleSection(result.estimatedHarvestDays)

            Spacer(Modifier.height(24.dp))

            // Location & Timestamp Cards
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LocationTimestampCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocationOn,
                    label = "LOCATION",
                    line1 = "Lat: %.4f N".format(Locale.getDefault(), result.latitude),
                    line2 = "Long: %.4f E".format(Locale.getDefault(), result.longitude)
                )

                val date = Date(result.timestamp)
                LocationTimestampCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    label = "TIMESTAMP",
                    line1 = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date),
                    line2 = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
