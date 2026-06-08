package com.smartleaf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.core.entry.entriesOf
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.theme.VibrantGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YieldAnalyticsDetailScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.dashboardStats.collectAsState(initial = emptyList())

    val total = stats.size.coerceAtLeast(1)
    val matureCount = stats.count { it.maturityPhase == "Mature" }
    val pseudoCount = stats.count { it.maturityPhase == "Pseudomature" }
    val immatureCount = stats.count { it.maturityPhase.contains("Immature", true) || it.maturityPhase.contains("Hyper", true) }
    
    val maturePct = (matureCount.toFloat() / total) * 100f
    val pseudoPct = (pseudoCount.toFloat() / total) * 100f
    val immaturePct = (immatureCount.toFloat() / total) * 100f

    val chartEntryModel = entryModelOf(
        entriesOf(maturePct),
        entriesOf(pseudoPct),
        entriesOf(immaturePct)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yield Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                Text("Overall Maturity Distribution", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Chart(
                            chart = columnChart(
                                columns = listOf(
                                    lineComponent(color = VibrantGreen, thickness = 16.dp),
                                    lineComponent(color = Color(0xFFFFCA28), thickness = 16.dp),
                                    lineComponent(color = Color(0xFFEF5350), thickness = 16.dp)
                                )
                            ),
                            model = chartEntryModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("Detailed Breakdown", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Phase", fontWeight = FontWeight.SemiBold, color = Color.Gray)
                            Text("Percentage", fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))
                        
                        BreakdownRow("Mature", "${"%.1f".format(maturePct)}%", VibrantGreen)
                        BreakdownRow("Pseudomature", "${"%.1f".format(pseudoPct)}%", Color(0xFFFFCA28))
                        BreakdownRow("Immature", "${"%.1f".format(immaturePct)}%", Color(0xFFEF5350))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            item {
                Text("Total Scans: ${stats.size}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
            }
        }
    }
}

@Composable
fun BreakdownRow(label: String, value: String, dotColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Medium)
        }
        Text(value, fontWeight = FontWeight.Bold)
    }
}
