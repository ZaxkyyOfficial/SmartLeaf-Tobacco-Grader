package com.smartleaf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartleaf.app.R
import com.smartleaf.app.data.local.SmartLeafResult
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.theme.VibrantGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onNavigateToDetail: (Long) -> Unit) {
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var selectedItemForEdit by remember { mutableStateOf<SmartLeafResult?>(null) }
    var newScanName by remember { mutableStateOf("") }

    if (showEditDialog) {
        val item = selectedItemForEdit
        if (item != null) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Rename Scan", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newScanName,
                        onValueChange = { newScanName = it },
                        label = { Text("Scan Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VibrantGreen),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateScanName(item.id, newScanName)
                            showEditDialog = false
                        }
                    ) {
                        Text("Save", color = VibrantGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color.White
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(top = 16.dp, start = 24.dp, end = 24.dp)
    ) {
        Text("Scan History", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by ID, name, or phase...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedBorderColor = VibrantGreen
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(searchResults.size) { index ->
                val item = searchResults[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onNavigateToDetail(item.id) },
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
                                .size(40.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_leaf_logo),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = VibrantGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val displayName = if (item.scanName.isNotEmpty()) item.scanName else "Batch #${item.id}"
                            Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val formattedTime = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                            Text(
                                text = formattedTime,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        val tagColor = if (item.maturityPhase.contains("Pseudo", true)) Color(0xFFFFCA28) 
                                       else if (item.maturityPhase.contains("Mature", true)) VibrantGreen 
                                       else Color(0xFFEF5350)
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tagColor.copy(alpha=0.15f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = item.maturityPhase.take(6), 
                                color = tagColor, 
                                fontSize = 10.sp, 
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                selectedItemForEdit = item
                                newScanName = item.scanName
                                showEditDialog = true
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.deleteScanRecord(item.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha=0.7f))
                        }
                    }
                }
            }
            if (searchResults.isEmpty()) {
                item {
                    Text(
                        "No scan history available.", 
                        color = Color.Gray, 
                        modifier = Modifier.padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
