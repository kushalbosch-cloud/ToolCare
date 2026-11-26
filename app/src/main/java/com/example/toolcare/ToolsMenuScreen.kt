package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MenuOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val badgeCount: Int = 0,
    val targetScreen: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsMenuScreen(
    breakdownCount: Int = 5,
    onNavigate: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    BackHandler { onBackClick() }

    val menuOptions = remember(breakdownCount) {
        listOf(
            MenuOption("Tool Master", "View all tools & details", Icons.Default.Build, Color(0xFF2563EB), 0, "Tool Master"),
            MenuOption("Original Tools", "Manage original line tools list", Icons.Default.Build, Color(0xFF2563EB), 0, "Original Tools"),
            MenuOption("Backup Tools", "Manage backup tool inventory", Icons.Default.Build, Color(0xFF10B981), 0, "Backup Tools"),
            MenuOption("PM Management", "Schedule & history", Icons.Default.CheckCircle, Color(0xFF10B981), 0, "PM Management"),
            MenuOption("Calibration", "Torque & verification", Icons.Default.Settings, Color(0xFF7C3AED), 0, "Calibration"),
            MenuOption("Breakdown", "Report & track issues", Icons.Default.Edit, Color(0xFFDC2626), breakdownCount, "Breakdown"),
            MenuOption("Repair", "Repair history & parts", Icons.Default.Build, Color(0xFFD97706), 0, "Repair"),
            MenuOption("Spare Parts", "Stock & consumption", Icons.Default.Settings, Color(0xFF0D9488), 0, "Spare Parts"),
            MenuOption("Backup & Restore Data", "Database export & import", Icons.Default.Refresh, Color(0xFF0F172A), 0, "Backup & Restore"),
            MenuOption("Parameter", "Settings & changes", Icons.Default.Settings, Color(0xFF475569), 0, "Parameter"),
            MenuOption("Documents", "Manuals & certificates", Icons.Default.Edit, Color(0xFF475569), 0, "Documents"),
            MenuOption("Issue / Return", "Tool allocation", Icons.Default.Person, Color(0xFFDB2777), 0, "Issue / Return"),
            MenuOption("History", "Complete tool history", Icons.Default.Refresh, Color(0xFF0F172A), 0, "History")
        )
    }

    val filteredOptions = menuOptions.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.subtitle.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools & Maintenance", fontWeight = FontWeight.Bold) },
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
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search menu...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp)),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredOptions) { option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(option.targetScreen) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(option.iconBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = option.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = option.subtitle,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (option.badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option.badgeCount.toString(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Text(">", fontSize = 18.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
