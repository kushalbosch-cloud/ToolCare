package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterSettingsScreen(
    tool: OriginalTool? = null,
    userRole: String = "technician",
    firebaseManager: FirebaseManager? = null,
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val currentTool = tool ?: OriginalTool(toolId = "T-025", toolName = "Bosch EXACT ION 12-700", model = "Bosch EXACT ION 12-700")

    var targetTorque by remember(currentTool) { mutableStateOf(currentTool.torque.ifEmpty { "12.00 Nm" }) }
    var minTorque by remember(currentTool) { mutableStateOf("10.00 Nm") }
    var maxTorque by remember(currentTool) { mutableStateOf("14.50 Nm") }
    var rpmRating by remember(currentTool) { mutableStateOf(currentTool.rpm.ifEmpty { "700 RPM" }) }
    var operatingVoltage by remember(currentTool) { mutableStateOf("18.0 V") }
    var pokaYokeMode by remember(currentTool) { mutableStateOf("Controller Interlock Active") }
    var shutoffStrategy by remember(currentTool) { mutableStateOf("Transducer / Clutch Shutoff") }
    var isEditing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parameter Settings (${currentTool.toolId})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Home, contentDescription = "Back")
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
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(currentTool.toolId, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                        Text("Model: ${currentTool.model.ifEmpty { currentTool.toolName }}", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
            }

            // Parameters Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bosch Technical Parameters", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                        if (userRole == "admin" && !isEditing) {
                            Button(onClick = { isEditing = true }) {
                                Text("Edit Config")
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    if (isEditing && userRole == "admin") {
                        OutlinedTextField(
                            value = targetTorque,
                            onValueChange = { targetTorque = it },
                            label = { Text("Target Torque (Nm)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minTorque,
                                onValueChange = { minTorque = it },
                                label = { Text("Min Torque Limit") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxTorque,
                                onValueChange = { maxTorque = it },
                                label = { Text("Max Torque Limit") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = rpmRating,
                            onValueChange = { rpmRating = it },
                            label = { Text("RPM Speed Rating") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = operatingVoltage,
                            onValueChange = { operatingVoltage = it },
                            label = { Text("Operating Voltage (V)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pokaYokeMode,
                            onValueChange = { pokaYokeMode = it },
                            label = { Text("Poka-Yoke Controller Setting") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = shutoffStrategy,
                            onValueChange = { shutoffStrategy = it },
                            label = { Text("Shut-off Strategy") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val updated = currentTool.copy(torque = targetTorque, rpm = rpmRating)
                                firebaseManager?.saveOriginalTool(updated)
                                isEditing = false
                                Toast.makeText(context, "Parameters updated successfully!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Save Parameter Configuration", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        ParamRow("Target Torque", targetTorque)
                        ParamRow("Min Torque Limit", minTorque)
                        ParamRow("Max Torque Limit", maxTorque)
                        ParamRow("RPM Speed Rating", rpmRating)
                        ParamRow("Operating Voltage", operatingVoltage)
                        ParamRow("Poka-Yoke Controller Interlock", pokaYokeMode)
                        ParamRow("Shut-off Strategy", shutoffStrategy)
                        ParamRow("Brand Scope", "Bosch Tightening System")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}
