package com.smartleaf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartleaf.app.R
import com.smartleaf.app.data.local.SmartLeafResult
import com.smartleaf.app.ui.theme.VibrantGreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsScreen(stats: List<SmartLeafResult>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // App Bar / Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibrantGreen, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .padding(24.dp)
        ) {
            Column {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Grading History", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Your past scan records", fontSize = 14.sp, color = Color.White.copy(alpha=0.8f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(stats.size) { index ->
                val item = stats[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_leaf_logo),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = VibrantGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Batch #${item.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val formattedTime = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                            Text(
                                text = formattedTime,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        
                        val tagColor = if (item.maturityPhase.contains("Pseudo", true)) Color(0xFFFFCA28) 
                                       else if (item.maturityPhase.contains("Mature", true)) VibrantGreen 
                                       else Color(0xFFEF5350)
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = tagColor.copy(alpha=0.15f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = item.maturityPhase, 
                                    color = tagColor, 
                                    fontSize = 10.sp, 
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${"%.1f".format(item.confidence)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
            if (stats.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No grading history available.", color = Color.Gray)
                    }
                }
            }
        }
    }
}
