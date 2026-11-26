package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolAllocationScreen(
    tool: OriginalTool? = null,
    firebaseManager: FirebaseManager? = null,
    userName: String = "Technician",
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current

    if (tool == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Issue / Return Allocation", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text("Tool निवडा", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Text("कृपया ॲलोकेशन करण्यासाठी टूल्स लिस्टमधून एक टूल निवडा.", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
        return
    }

    var currentTool by remember(tool) { mutableStateOf(tool) }
    var assignedLine by remember(currentTool) { mutableStateOf(currentTool.line.ifEmpty { "Line-1" }) }
    var assignedStation by remember(currentTool) { mutableStateOf(currentTool.station.ifEmpty { "ST-01" }) }
    var issuedToPerson by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val isFirebaseAvailable = firebaseManager != null

    val isStoreStation = currentTool.station.equals("Standby Store", ignoreCase = true) ||
                         currentTool.station.equals("Store", ignoreCase = true)

    val statusBadgeText = if (isStoreStation) "IN STORE (Standby)" else "ISSUED TO LINE"
    val statusBgColor = if (isStoreStation) Color(0xFFEFF6FF) else Color(0xFFECFDF5)
    val statusTxtColor = if (isStoreStation) Color(0xFF2563EB) else Color(0xFF10B981)

    val toolStatusLower = currentTool.status.trim().lowercase(Locale.ROOT)
    val isToolInBreakdown = toolStatusLower.contains("breakdown") || toolStatusLower.contains("repair") || toolStatusLower.contains("missing")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Issue / Return Allocation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isFirebaseAvailable) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), modifier = Modifier.fillMaxWidth()) {
                    Text("⚠️ Database connection unavailable. Allocation actions disabled.", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }

            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tool Assignment Tracker", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Text("Tool ID: ${currentTool.toolId} • ${currentTool.model.ifEmpty { currentTool.toolName }}", fontSize = 13.sp, color = Color(0xFF64748B))
                    
                    if (isToolInBreakdown) {
                        Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(6.dp)) {
                            Text("⚠️ Tool Status: ${currentTool.status} (Cannot be issued)", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allocation Status:", fontSize = 13.sp, color = Color(0xFF64748B))
                        Surface(
                            color = statusBgColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = statusBadgeText,
                                color = statusTxtColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Allocation Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Allocation Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    OutlinedTextField(
                        value = assignedLine,
                        onValueChange = { assignedLine = it },
                        label = { Text("Target Line Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isFirebaseAvailable && !isSubmitting
                    )

                    OutlinedTextField(
                        value = assignedStation,
                        onValueChange = { assignedStation = it },
                        label = { Text("Target Station Number") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isFirebaseAvailable && !isSubmitting
                    )

                    OutlinedTextField(
                        value = issuedToPerson,
                        onValueChange = { issuedToPerson = it },
                        label = { Text("Technician / Operator Name *") },
                        placeholder = { Text("e.g. Technician Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isFirebaseAvailable && !isSubmitting
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Issue and Return Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = isFirebaseAvailable && !isSubmitting && !isToolInBreakdown,
                            onClick = {
                                if (isToolInBreakdown) {
                                    Toast.makeText(context, "टूल ${currentTool.toolId} मॅन्युफॅक्चरिंग स्टेटस '${currentTool.status}' असल्यामुळे इश्यू करता येत नाही.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (issuedToPerson.isBlank()) {
                                    Toast.makeText(context, "कृपया टेक्निशिअन / ऑपरेटर नाव टाका.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSubmitting = true
                                firebaseManager?.issueTool(
                                    tool = currentTool,
                                    line = assignedLine,
                                    station = assignedStation,
                                    person = issuedToPerson.trim(),
                                    currentUser = userName,
                                    onResult = { success, msg ->
                                        isSubmitting = false
                                        Toast.makeText(context, msg, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                        if (success) {
                                            currentTool = currentTool.copy(line = assignedLine, station = assignedStation)
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Issue Tool", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Button(
                            enabled = isFirebaseAvailable && !isSubmitting,
                            onClick = {
                                isSubmitting = true
                                firebaseManager?.returnTool(
                                    tool = currentTool,
                                    currentUser = userName,
                                    onResult = { success, msg ->
                                        isSubmitting = false
                                        Toast.makeText(context, msg, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                        if (success) {
                                            assignedLine = "Standby Store"
                                            assignedStation = "Standby Store"
                                            currentTool = currentTool.copy(line = "Standby Store", station = "Standby Store")
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Return Tool", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
