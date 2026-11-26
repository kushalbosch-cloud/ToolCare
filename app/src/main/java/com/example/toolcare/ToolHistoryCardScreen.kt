package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolHistoryCardScreen(
    tool: OriginalTool,
    breakdownList: List<BreakdownEntry>,
    pmEntries: List<PmEntry>,
    userRole: String, // New parameter
    onEditClick: (OriginalTool) -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    // Combine breakdown repairs and PMs into a single chronological log
    val historyLog = remember(tool, breakdownList, pmEntries) {
        val logs = mutableListOf<MaintenanceLogRow>()
        
        // Add Breakdown Repairs
        breakdownList.filter { it != null && it.originalToolId == tool.toolId && it.status == "Handover Completed" }.forEach { entry ->
            val timestampDate = (entry.timestamp ?: "").split(" ").firstOrNull() ?: ""
            logs.add(MaintenanceLogRow(
                receivedDate = timestampDate.ifEmpty { "N/A" },
                problem = entry.problem ?: "",
                repairedDate = timestampDate.ifEmpty { "N/A" },
                status = "REPAIRED",
                spares = (entry.repairDetails ?: "").ifEmpty { "N/A" },
                engineer = entry.handoverPerson ?: "N/A",
                officer = "M&M"
            ))
        }
        
        // Add PMs
        pmEntries.filter { it != null && it.toolId == tool.toolId && it.status == "Done" }.forEach { entry ->
            val compDate = (entry.completedDate ?: "").split(" ").firstOrNull() ?: ""
            logs.add(MaintenanceLogRow(
                receivedDate = entry.scheduledDate ?: "N/A",
                problem = "PREVENTIVE MAINTENANCE",
                repairedDate = compDate.ifEmpty { entry.scheduledDate ?: "N/A" },
                status = "PM DONE",
                spares = entry.partsUsed ?: "No Parts Used",
                engineer = "Service Engg",
                officer = "M&M"
            ))
        }
        
        logs.sortByDescending { it.repairedDate }
        logs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tool History Card") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) { Text("Back") }
                },
                actions = {
                    if (userRole == "admin") {
                        IconButton(onClick = { onEditClick(tool) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Tool")
                        }
                    }
                    IconButton(onClick = { /* Export PDF/Excel Placeholder */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BOSCH BATTERY TOOLS HISTORY CARD", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DetailItem("Make", "Bosch", Modifier.weight(1f))
                            DetailItem("Model", tool.model, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DetailItem("Sr. No.", tool.serialNumber, Modifier.weight(1f))
                            DetailItem("Torque", tool.torque, Modifier.weight(1f))
                            DetailItem("RPM", tool.rpm, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Mahindra Details
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("MAHINDRA & MAHINDRA DETAILS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        DetailItem("M&M Tool ID", tool.toolId)
                        DetailItem("Application", tool.application)
                        DetailItem("Line", tool.line)
                        DetailItem("Install Date", tool.installDate)

                        val health = ToolHealthHelper.getToolHealthStatus(tool.toolId, breakdownList, pmEntries)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("HEALTH STATUS", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = health,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (health.contains("High Risk")) Color.Red else Color(0xFF059669)
                        )
                    }
                }
            }

            // Maintenance Table
            item {
                Text("REPAIR & MAINTENANCE LOG", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            items(
                historyLog,
                key = { row -> "${row.receivedDate}_${row.repairedDate}_${row.hashCode()}" }
            ) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Received: ${row.receivedDate}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Repaired: ${row.repairedDate}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Problem: ${row.problem}", fontSize = 13.sp)
                        Text("Status: ${row.status}", color = Color(0xFF059669), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Spares: ${row.spares}", fontSize = 12.sp)
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            Text("Engg: ${row.engineer}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                            Text("Officer: ${row.officer}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value.ifEmpty { "N/A" }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

data class MaintenanceLogRow(
    val receivedDate: String,
    val problem: String,
    val repairedDate: String,
    val status: String,
    val spares: String,
    val engineer: String,
    val officer: String
)
