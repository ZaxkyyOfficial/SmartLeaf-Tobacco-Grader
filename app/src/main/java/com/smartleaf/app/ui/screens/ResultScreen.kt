package com.smartleaf.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartleaf.app.R
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.ProcessingState
import com.smartleaf.app.ui.theme.VibrantGreen
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: MainViewModel,
    onBackToHome: () -> Unit
) {
    val state by viewModel.processingState.collectAsState()

    // 1. Safety Check State
    val resultState = state as? ProcessingState.Success ?: return
    val result = resultState.result
    val context = androidx.compose.ui.platform.LocalContext.current

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

            // Bounding Box UI
            Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .fillMaxHeight(0.6f)
                        .border(2.dp, VibrantGreen, RoundedCornerShape(8.dp))
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-10).dp),
                        color = VibrantGreen,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "ROI DETECTED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
                    onClick = onBackToHome,
                    modifier = Modifier.background(Color.Black.copy(alpha=0.4f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "Analysis Result",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f)
                    )
                )
                IconButton(
                    onClick = { 
                        val shareText = "SmartLeaf Scan: ${result.maturityPhase} / ${result.qualityGrade} (Confidence: ${"%.1f".format(result.confidence)}%). Location: [${result.latitude}, ${result.longitude}]. Harvest: ${result.estimatedHarvestDays} Days"
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            if (result.imageUri != null) {
                                type = "image/jpeg"
                                putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(result.imageUri))
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share SmartLeaf Result"))
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha=0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = VibrantGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CLASSIFICATION COMPLETE", color = VibrantGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(result.maturityPhase, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(result.qualityGrade, fontSize = 16.sp, color = VibrantGreen)

                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Divider(color = Color(0xFFE2E8F0))
                    Spacer(Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("CONFIDENCE", "${"%.1f".format(result.confidence)}%")
                        StatItem("COLOR", result.colorCode)
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

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.saveResult(result)
                    onBackToHome()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VibrantGreen)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Save to Local Database", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}


// --- HELPER COMPONENTS ---

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HarvestScheduleSection(days: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, null, tint = VibrantGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Harvest Scheduling", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val daysText = if (days > 0) "in $days Days" else "Now"
                    Text("Optimal Harvest $daysText", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brightness7, null, tint = Color(0xFFFFCA28), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Weather: Sunny, 26°C", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = VibrantGreen)
            }
        }
    }
}

@Composable
fun LocationTimestampCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    line1: String,
    line2: String
) {
    Card(
        modifier = modifier, // Modifier weight dilewatkan dari luar
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(line1, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(line2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}