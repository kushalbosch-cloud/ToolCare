package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "Just now"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes mins ago"
        hours < 24 -> "$hours hrs ago"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    firebaseManager: FirebaseManager,
    company: String,
    block: String,
    userRole: String = "technician",
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String, String) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val notificationsList by firebaseManager.getNotifications(company, block).collectAsState(initial = emptyList())

    var selectedFilterTab by remember { mutableStateOf("ALL") } // ALL, UNREAD, PM, BREAKDOWN, BACKUP, STOCK, ADMIN
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val unreadCount = remember(notificationsList) { notificationsList.count { !it.isRead } }

    val filteredList = remember(notificationsList, selectedFilterTab, searchQuery, userRole) {
        val tabList = when (selectedFilterTab) {
            "UNREAD" -> notificationsList.filter { !it.isRead }
            "PM" -> notificationsList.filter { it.category.equals("PM", ignoreCase = true) }
            "BREAKDOWN" -> notificationsList.filter { it.category.equals("BREAKDOWN", ignoreCase = true) }
            "BACKUP" -> notificationsList.filter { it.category.equals("BACKUP", ignoreCase = true) }
            "STOCK" -> notificationsList.filter { it.category.equals("STOCK", ignoreCase = true) }
            "ADMIN" -> notificationsList.filter { it.category.equals("ADMIN", ignoreCase = true) || it.category.equals("SYSTEM", ignoreCase = true) }
            else -> notificationsList
        }

        if (searchQuery.isBlank()) tabList
        else tabList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.message.contains(searchQuery, ignoreCase = true) ||
            it.toolId.contains(searchQuery, ignoreCase = true) ||
            it.partId.contains(searchQuery, ignoreCase = true) ||
            it.line.contains(searchQuery, ignoreCase = true) ||
            it.station.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notification Center", fontWeight = FontWeight.Bold, color = Color.White)
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFDC2626),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = {
                            firebaseManager.markAllNotificationsAsRead(company, block)
                            Toast.makeText(context, "All notifications marked as read", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Mark All Read", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Notification Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title, tool ID, part, line, message...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Category & Priority Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    "ALL" to "All (${notificationsList.size})",
                    "UNREAD" to "Unread ($unreadCount)",
                    "PM" to "PM",
                    "BREAKDOWN" to "Breakdowns",
                    "BACKUP" to "Backup Tools",
                    "STOCK" to "Stock",
                    "ADMIN" to "Admin"
                )

                items(tabs) { (key, label) ->
                    val isSelected = selectedFilterTab == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterTab = key },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF1F5F9),
                            labelColor = Color(0xFF0F172A)
                        )
                    )
                }
            }

            // Notification List
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No notifications found", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.notificationId.ifEmpty { "${it.createdAt}_${it.hashCode()}" } }) { notif ->
                        NotificationCard(
                            notification = notif,
                            onClick = {
                                if (!notif.isRead) {
                                    firebaseManager.markNotificationAsRead(notif.notificationId)
                                }
                                val route = notif.actionRoute.ifEmpty {
                                    when (notif.category.uppercase(Locale.ROOT)) {
                                        "PM" -> "pm"
                                        "BREAKDOWN" -> "breakdown"
                                        "BACKUP" -> "backup"
                                        "STOCK" -> "stock"
                                        "ADMIN" -> "admin"
                                        else -> "breakdown"
                                    }
                                }
                                val entityId = notif.toolId.ifEmpty { notif.partId }
                                onNavigateToRoute(route, entityId)
                            },
                            onDelete = {
                                firebaseManager.deleteNotification(notif.notificationId)
                                Toast.makeText(context, "Notification dismissed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        NotificationSettingsDialog(onDismiss = { showSettingsDialog = false })
    }
}

@Composable
fun NotificationCard(
    notification: NotificationModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isHigh = notification.priority.equals("HIGH", ignoreCase = true)
    val isAction = notification.priority.equals("ACTION_REQUIRED", ignoreCase = true)
    val isSuccess = notification.priority.equals("SUCCESS", ignoreCase = true)

    val badgeBg = when {
        isHigh -> Color(0xFFFEF2F2)
        isAction -> Color(0xFFFFFBEB)
        isSuccess -> Color(0xFFECFDF5)
        else -> Color(0xFFEFF6FF)
    }

    val iconColor = when {
        isHigh -> Color(0xFFDC2626)
        isAction -> Color(0xFFD97706)
        isSuccess -> Color(0xFF10B981)
        else -> Color(0xFF2563EB)
    }

    val priorityTag = when {
        isHigh -> "🔴 CRITICAL"
        isAction -> "🟠 ACTION REQUIRED"
        isSuccess -> "🟢 COMPLETED"
        else -> "🔵 INFO"
    }

    val iconVector = when {
        isHigh -> Icons.Default.Warning
        isAction -> Icons.Default.Warning
        isSuccess -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }

    val actionButtonText = when (notification.category.uppercase(Locale.ROOT)) {
        "BREAKDOWN" -> "View Breakdown →"
        "PM" -> "View PM Task →"
        "BACKUP" -> "View Backup Tool →"
        "STOCK" -> "View Stock Item →"
        "ADMIN" -> "View Admin Details →"
        else -> "View Details →"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (notification.isRead) Color.White else Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Priority Icon Box
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(badgeBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = iconVector, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notification.title,
                            fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = priorityTag,
                                color = iconColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!notification.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF2563EB), CircleShape)
                            )
                        }
                    }

                    Text(
                        text = notification.message,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // Entity Metadata Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (notification.toolId.isNotBlank()) {
                        Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Tool: ${notification.toolId}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (notification.partId.isNotBlank()) {
                        Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Part: ${notification.partId}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (notification.line.isNotBlank()) {
                        Text(
                            text = "${notification.line} (${notification.station})",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Text(
                    text = formatRelativeTime(notification.createdAt),
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Action Button
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Text(actionButtonText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun NotificationSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pmAlerts by remember { mutableStateOf(true) }
    var breakdownAlerts by remember { mutableStateOf(true) }
    var backupAlerts by remember { mutableStateOf(true) }
    var stockAlerts by remember { mutableStateOf(true) }
    var adminAlerts by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Preferences", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Alert Channels", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

                PreferenceToggleRow("Breakdown & Repair Alerts", breakdownAlerts) { breakdownAlerts = it }
                PreferenceToggleRow("PM Task Alerts", pmAlerts) { pmAlerts = it }
                PreferenceToggleRow("Backup Tool Alerts", backupAlerts) { backupAlerts = it }
                PreferenceToggleRow("Stock & Inventory Alerts", stockAlerts) { stockAlerts = it }
                PreferenceToggleRow("Admin & System Announcements", adminAlerts) { adminAlerts = it }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Text("Sound & Feedback", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                PreferenceToggleRow("Notification Sound", soundEnabled) { soundEnabled = it }
                PreferenceToggleRow("Vibration Alert", vibrationEnabled) { vibrationEnabled = it }

                Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Note: Critical breakdown & emergency halt safety alerts are enforced by Factory Admin and cannot be muted.",
                        fontSize = 10.sp,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(context, "Notification Preferences Saved", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save Preferences")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PreferenceToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF0F172A))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

