package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ActionGridItem(
    val label: String,
    val icon: ImageVector,
    val bgColor: Color,
    val targetScreen: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailsScreen(
    tool: OriginalTool? = null,
    onNavigate: (String) -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val toolId = tool?.toolId?.ifEmpty { "T-025" } ?: "T-025"
    val toolName = tool?.toolName?.ifEmpty { "Bosch EXACT ION 12-700" } ?: "Bosch EXACT ION 12-700"
    val serialNo = tool?.serialNumber?.ifEmpty { "4 001 123 456" } ?: "4 001 123 456"
    val station = tool?.station?.ifEmpty { "S-12" } ?: "S-12"
    val line = tool?.line?.ifEmpty { "Line-1" } ?: "Line-1"
    val status = tool?.status?.ifEmpty { "OK" } ?: "OK"

    val gridActions = remember {
        listOf(
            ActionGridItem("Tool Details", Icons.Default.Info, Color(0xFF2563EB), "Tool Master"),
            ActionGridItem("PM", Icons.Default.CheckCircle, Color(0xFF10B981), "PM Management"),
            ActionGridItem("Calibration", Icons.Default.Settings, Color(0xFF2563EB), "Calibration"),
            ActionGridItem("Breakdown", Icons.Default.Edit, Color(0xFFDC2626), "Breakdown"),
            ActionGridItem("Repair History", Icons.Default.Build, Color(0xFFD97706), "Tool History Card"),
            ActionGridItem("Parameter", Icons.Default.Settings, Color(0xFF0F172A), "Parameter Settings"),
            ActionGridItem("Spare Parts", Icons.Default.Settings, Color(0xFF0D9488), "Spare Parts"),
            ActionGridItem("Documents", Icons.Default.Edit, Color(0xFF4F46E5), "Documents"),
            ActionGridItem("Allocation", Icons.Default.Refresh, Color(0xFF0284C7), "Tool Allocation")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR / Tool Details", fontWeight = FontWeight.Bold) },
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
            // Top Header Card with Drill Image
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_drill_tool),
                        contentDescription = "Tool Image",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(6.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (status == "OK") Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (status == "OK") Color(0xFF10B981) else Color(0xFFDC2626))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = status,
                                        color = if (status == "OK") Color(0xFF10B981) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(text = toolId, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(text = toolName, fontSize = 13.sp, color = Color(0xFF64748B))
                    }

                    // QR Code Icon
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "QR Code",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Specs Table Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecRow("Serial No.", serialNo)
                    SpecRow("Station", station)
                    SpecRow("Line", line)
                    SpecRow("Department", "Assembly")
                    Divider(color = Color(0xFFF1F5F9))
                    SpecRow("Last PM", "10 Sep 2026")
                    SpecRow("Next PM", "10 Dec 2026")
                    SpecRow("Last Calibration", "10 Sep 2026")
                    SpecRow("Next Calibration", "10 Oct 2026")
                }
            }

            // 9 Action Grid Buttons
            Text("Quick Tool Actions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

            val rows = gridActions.chunked(3)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(item.targetScreen) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(item.bgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                    // Fill remaining empty grid cells if any
                    if (rowItems.size < 3) {
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}
