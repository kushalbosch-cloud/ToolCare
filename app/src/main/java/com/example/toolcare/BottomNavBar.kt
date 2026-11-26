package com.example.toolcare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("Home", "Home", Icons.Default.Home)
    object Tools : BottomNavItem("Tools Menu", "Tools", Icons.Default.Build)
    object Scan : BottomNavItem("Scan QR", "Scan", Icons.Default.Search)
    object Maintenance : BottomNavItem("PM Management", "Maintenance", Icons.Default.Settings)
    object Reports : BottomNavItem("Reports Dashboard", "Reports", Icons.Default.Person)
}

@Composable
fun ToolCareBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Tools,
        BottomNavItem.Scan,
        BottomNavItem.Maintenance,
        BottomNavItem.Reports
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = when (item) {
                    BottomNavItem.Home -> currentRoute == "Home"
                    BottomNavItem.Tools -> currentRoute == "Tools Menu" || currentRoute == "Original Tools" || currentRoute == "Backup Tools" || currentRoute == "Tool Master"
                    BottomNavItem.Scan -> currentRoute == "Scan QR" || currentRoute == "Tool Details"
                    BottomNavItem.Maintenance -> currentRoute == "PM Management" || currentRoute == "Calibration" || currentRoute == "Breakdown" || currentRoute == "Repair"
                    BottomNavItem.Reports -> currentRoute == "Reports Dashboard" || currentRoute == "Dashboard"
                }

                if (item == BottomNavItem.Scan) {
                    // Center Floating Highlight Scanner Icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .clickable { onTabSelected(item.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    val activeColor = Color(0xFF2563EB)
                    val inactiveColor = Color(0xFF64748B)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) activeColor.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onTabSelected(item.route) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) activeColor else inactiveColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) activeColor else inactiveColor
                        )
                    }
                }
            }
        }
    }
}
