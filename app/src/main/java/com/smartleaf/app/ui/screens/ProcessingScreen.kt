package com.smartleaf.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartleaf.app.R
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.ProcessingState
import com.smartleaf.app.ui.theme.VibrantGreen

@Composable
fun ProcessingScreen(
    viewModel: MainViewModel,
    onProcessingComplete: () -> Unit
) {
    val state by viewModel.processingState.collectAsState()

    LaunchedEffect(state) {
        if (state is ProcessingState.Success) {
            onProcessingComplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    color = VibrantGreen,
                    strokeWidth = 4.dp
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_leaf_logo),
                    contentDescription = "Neural Network processing",
                    modifier = Modifier.size(48.dp),
                    colorFilter = ColorFilter.tint(VibrantGreen)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Processing Dual-AI...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Analyzing color, texture, and estimating optimal harvest schedule.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
