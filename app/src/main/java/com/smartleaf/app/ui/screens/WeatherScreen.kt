package com.smartleaf.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartleaf.app.ui.theme.VibrantGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeatherScreen(latitude: Double = -6.2088, longitude: Double = 106.8456) {
    var temp by remember { mutableStateOf<Double?>(null) }
    var windSpeed by remember { mutableStateOf<Double?>(null) }
    var condition by remember { mutableStateOf("Loading...") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(latitude, longitude) {
        withContext(Dispatchers.IO) {
            try {
                // Open-Meteo free tier API
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true"
                val response = URL(url).readText()
                val json = JSONObject(response)
                val current = json.getJSONObject("current_weather")
                
                temp = current.getDouble("temperature")
                windSpeed = current.getDouble("windspeed")
                val weatherCode = current.getInt("weathercode")
                
                condition = when (weatherCode) {
                    0 -> "Clear Sky"
                    1, 2, 3 -> "Partly Cloudy"
                    45, 48 -> "Fog"
                    51, 53, 55 -> "Drizzle"
                    61, 63, 65 -> "Rain"
                    71, 73, 75 -> "Snow"
                    95 -> "Thunderstorm"
                    else -> "Unknown"
                }
            } catch (e: Exception) {
                condition = "Offline/Unavailable"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text("Local Weather", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Coordinates: ${"%.2f".format(latitude)}, ${"%.2f".format(longitude)}", color = Color.Gray, fontSize = 12.sp)
        
        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = VibrantGreen)
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = VibrantGreen),
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(condition, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (temp != null) "${"%.1f".format(temp)}°C" else "--",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1f).height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (temp != null) "${"%.1f".format(temp)}°C" else "--", fontWeight = FontWeight.Bold)
                        Text("Temperature", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Air, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (windSpeed != null) "${"%.1f".format(windSpeed)} km/h" else "--", fontWeight = FontWeight.Bold)
                        Text("Wind Speed", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
