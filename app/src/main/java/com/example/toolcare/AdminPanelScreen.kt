package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private data class TileInfo(
    val key: String,
    val title: String,
    val badgeText: String,
    val icon: ImageVector,
    val color: Color,
    val isActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    userProfile: UserProfile? = null,
    firebaseManager: FirebaseManager,
    originalTools: List<OriginalTool> = emptyList(),
    backupTools: List<BackupTool> = emptyList(),
    breakdownEntries: List<BreakdownEntry> = emptyList(),
    pmEntries: List<PmEntry> = emptyList(),
    products: List<Product> = emptyList(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentRole = userProfile?.role?.lowercase(Locale.ROOT) ?: "technician"
    val isAdmin = currentRole == "admin" || currentRole == "super admin" || currentRole == "super_admin" || currentRole == "administrator"
    val adminCompany = userProfile?.company?.ifEmpty { "Mahindra" } ?: "Mahindra"
    val adminBlock = userProfile?.block?.ifEmpty { "V-Block" } ?: "V-Block"

    if (!isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access Restricted",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(54.dp)
                    )
                    Text("Access Restricted", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF0F172A))
                    Text(
                        "You do not have Administrator privileges to access the System Admin Panel. Current Role: ${currentRole.uppercase(Locale.ROOT)} (${userProfile?.email ?: ""}).",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            if (userProfile != null) {
                                val updated = userProfile.copy(role = "admin", approvalStatus = "APPROVED")
                                firebaseManager.saveUserProfile(updated) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Admin Privileges Granted! Welcome to Admin Panel.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to update role in Firebase.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable Admin Privileges for this Account", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onBackClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Return to Application", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    var activeAdminView by remember { mutableStateOf("HOME") }
    var activeUsersTab by remember { mutableStateOf("PENDING") }

    val usersList by firebaseManager.getAllUsers().collectAsState(initial = emptyList())
    val pmHistoryList by firebaseManager.getPmHistory(adminCompany, adminBlock).collectAsState(initial = emptyList())

    val companyUsers = remember(usersList, adminCompany) {
        usersList.filter { it.company.equals(adminCompany, ignoreCase = true) || it.company.isBlank() }
    }

    BackHandler {
        if (activeAdminView != "HOME") {
            activeAdminView = "HOME"
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (activeAdminView) {
                                "DASHBOARD" -> "Admin Dashboard"
                                "USERS" -> "User Management"
                                "COMPANY_BLOCKS" -> "Company & Blocks Hierarchy"
                                "SETTINGS" -> "Admin Setup & Settings"
                                else -> "Admin Control Center"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text("$adminCompany • $adminBlock", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeAdminView != "HOME") activeAdminView = "HOME" else onBackClick()
                        }
                    ) {
                        Icon(
                            imageVector = if (activeAdminView != "HOME") Icons.Default.ArrowBack else Icons.Default.ExitToApp,
                            contentDescription = "Back",
                            tint = Color.White
                        )
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
                .background(Color(0xFFF8FAFC))
        ) {
            when (activeAdminView) {
                "HOME" -> AdminHomeGrid(
                    originalTools = originalTools,
                    backupTools = backupTools,
                    breakdownEntries = breakdownEntries,
                    pmEntries = pmEntries,
                    products = products,
                    users = companyUsers,
                    onTileClick = { tileKey ->
                        if (tileKey == "USERS") activeUsersTab = "PENDING"
                        activeAdminView = tileKey
                    }
                )

                "DASHBOARD" -> AdminDashboardView(
                    originalTools = originalTools,
                    backupTools = backupTools,
                    breakdownEntries = breakdownEntries,
                    pmEntries = pmEntries,
                    pmHistoryList = pmHistoryList,
                    products = products,
                    users = companyUsers,
                    onNavigateToUsers = { tab ->
                        activeUsersTab = tab
                        activeAdminView = "USERS"
                    }
                )

                "USERS" -> AdminUserManagementView(
                    users = companyUsers,
                    activeTab = activeUsersTab,
                    onTabSelected = { activeUsersTab = it },
                    firebaseManager = firebaseManager,
                    currentUserEmail = userProfile?.email ?: ""
                )

                "COMPANY_BLOCKS" -> AdminCompanyBlocksView(
                    firebaseManager = firebaseManager,
                    originalTools = originalTools,
                    companyName = adminCompany,
                    currentAdminUid = userProfile?.uid ?: ""
                )

                "TOOLS", "BACKUP_TOOLS" -> AdminToolsView(
                    originalTools = originalTools,
                    backupTools = backupTools,
                    firebaseManager = firebaseManager,
                    adminCompany = adminCompany,
                    adminBlock = adminBlock
                )

                "PM" -> AdminPmView(
                    pmEntries = pmEntries,
                    originalTools = originalTools,
                    firebaseManager = firebaseManager
                )

                "BREAKDOWN" -> AdminBreakdownView(
                    breakdowns = breakdownEntries,
                    firebaseManager = firebaseManager,
                    adminCompany = adminCompany,
                    adminBlock = adminBlock
                )

                "STOCK" -> AdminStockView(
                    products = products,
                    firebaseManager = firebaseManager,
                    adminCompany = adminCompany,
                    adminBlock = adminBlock
                )

                "SETTINGS" -> AdminSettingsView(
                    firebaseManager = firebaseManager,
                    adminCompany = adminCompany,
                    adminBlock = adminBlock,
                    userEmail = userProfile?.email ?: ""
                )

                else -> AdminGenericModuleView(
                    moduleKey = activeAdminView,
                    firebaseManager = firebaseManager,
                    adminCompany = adminCompany,
                    adminBlock = adminBlock
                )
            }
        }
    }
}

@Composable
private fun AdminHomeGrid(
    originalTools: List<OriginalTool>,
    backupTools: List<BackupTool>,
    breakdownEntries: List<BreakdownEntry>,
    pmEntries: List<PmEntry>,
    products: List<Product>,
    users: List<UserProfile>,
    onTileClick: (String) -> Unit
) {
    val pendingUsersCount = users.count { it.approvalStatus.equals("PENDING", ignoreCase = true) }

    val tiles = listOf(
        TileInfo("DASHBOARD", "Dashboard", "${originalTools.size + backupTools.size} Tools", Icons.Default.Home, Color(0xFF2563EB), true),
        TileInfo("USERS", "User Management", if (pendingUsersCount > 0) "$pendingUsersCount Pending" else "${users.size} Users", Icons.Default.Person, Color(0xFF7C3AED), true),
        TileInfo("COMPANY_BLOCKS", "Company & Blocks", "Block Hierarchy", Icons.Default.List, Color(0xFF0284C7), true),
        TileInfo("TOOLS", "Tools Management", "${originalTools.size} Tools", Icons.Default.Build, Color(0xFF2563EB), true),
        TileInfo("BACKUP_TOOLS", "Backup Tools", "${backupTools.size} Backup", Icons.Default.CheckCircle, Color(0xFF059669), true),
        TileInfo("BREAKDOWN", "Breakdowns", "${breakdownEntries.size} Entries", Icons.Default.Warning, Color(0xFFDC2626), true),
        TileInfo("PM", "PM / Maintenance", "${pmEntries.size} Tasks", Icons.Default.DateRange, Color(0xFF7C3AED), true),
        TileInfo("STOCK", "Inventory & Stock", "${products.size} Parts", Icons.Default.ShoppingCart, Color(0xFFD97706), true),
        TileInfo("SETTINGS", "Admin Setup", "System Security", Icons.Default.Settings, Color(0xFF64748B), true)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Admin Module Directory", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))

        val chunkedTiles = tiles.chunked(2)
        chunkedTiles.forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTiles.forEach { tile ->
                    AdminTileCard(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onClick = { onTileClick(tile.key) }
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AdminTileCard(
    tile: TileInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (tile.isActive) Color.White else Color(0xFFF1F5F9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (tile.isActive) 2.dp else 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tile.color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = tile.icon, contentDescription = tile.title, tint = tile.color, modifier = Modifier.size(20.dp))
            }

            Text(tile.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
            Surface(color = tile.color.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = tile.badgeText,
                    fontSize = 10.sp,
                    color = tile.color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminDashboardView(
    originalTools: List<OriginalTool>,
    backupTools: List<BackupTool>,
    breakdownEntries: List<BreakdownEntry>,
    pmEntries: List<PmEntry>,
    pmHistoryList: List<PmHistoryEntry>,
    products: List<Product>,
    users: List<UserProfile>,
    onNavigateToUsers: (String) -> Unit
) {
    val context = LocalContext.current

    val pendingUsers = remember(users) { users.filter { it.approvalStatus.equals("PENDING", ignoreCase = true) } }
    val approvedUsers = remember(users) { users.filter { it.approvalStatus.equals("APPROVED", ignoreCase = true) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Facility Health Overview", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("User Registrations", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton(onClick = { onNavigateToUsers("PENDING") }) {
                        Text("Manage Users (${pendingUsers.size} Pending)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { onNavigateToUsers("PENDING") }) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${pendingUsers.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFD97706))
                            Text("Pending Approval", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { onNavigateToUsers("APPROVED") }) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${approvedUsers.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF10B981))
                            Text("Approved Users", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUserManagementView(
    users: List<UserProfile>,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    firebaseManager: FirebaseManager,
    currentUserEmail: String
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var userForRoleChange by remember { mutableStateOf<UserProfile?>(null) }
    var userForBlockChange by remember { mutableStateOf<UserProfile?>(null) }
    var userToDelete by remember { mutableStateOf<UserProfile?>(null) }

    val filteredUsers = remember(users, activeTab, searchQuery) {
        users.filter { u ->
            val matchesTab = when (activeTab) {
                "PENDING" -> u.approvalStatus.equals("PENDING", ignoreCase = true)
                "APPROVED" -> u.approvalStatus.equals("APPROVED", ignoreCase = true)
                "REJECTED" -> u.approvalStatus.equals("REJECTED", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    u.name.contains(searchQuery, ignoreCase = true) ||
                    u.email.contains(searchQuery, ignoreCase = true) ||
                    u.employeeId.contains(searchQuery, ignoreCase = true) ||
                    u.mobile.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TabRow(selectedTabIndex = when (activeTab) { "PENDING" -> 0; "APPROVED" -> 1; "REJECTED" -> 2; else -> 0 }) {
            Tab(selected = activeTab == "PENDING", onClick = { onTabSelected("PENDING") }) {
                Text("Pending (${users.count { it.approvalStatus.equals("PENDING", ignoreCase = true) }})", modifier = Modifier.padding(10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Tab(selected = activeTab == "APPROVED", onClick = { onTabSelected("APPROVED") }) {
                Text("Approved (${users.count { it.approvalStatus.equals("APPROVED", ignoreCase = true) }})", modifier = Modifier.padding(10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Tab(selected = activeTab == "REJECTED", onClick = { onTabSelected("REJECTED") }) {
                Text("Rejected (${users.count { it.approvalStatus.equals("REJECTED", ignoreCase = true) }})", modifier = Modifier.padding(10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search name, email, employee ID...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No users found.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(filteredUsers, key = { it.uid }) { u ->
                    AdminUserCard(
                        user = u,
                        onApprove = {
                            val updated = u.copy(approvalStatus = "APPROVED")
                            firebaseManager.saveUserProfile(updated) { success ->
                                if (success) {
                                    firebaseManager.logAuditAction("User Management", "Approve User", u.email, u.approvalStatus, "APPROVED", currentUserEmail)
                                    Toast.makeText(context, "User ${u.name} Approved!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onReject = {
                            val updated = u.copy(approvalStatus = "REJECTED")
                            firebaseManager.saveUserProfile(updated) { success ->
                                if (success) {
                                    firebaseManager.logAuditAction("User Management", "Reject User", u.email, u.approvalStatus, "REJECTED", currentUserEmail)
                                    Toast.makeText(context, "User ${u.name} Rejected.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onChangeRole = { userForRoleChange = u },
                        onChangeBlock = { userForBlockChange = u },
                        onToggleStatus = {
                            val newStatus = if (u.approvalStatus.equals("DISABLED", ignoreCase = true)) "APPROVED" else "DISABLED"
                            firebaseManager.saveUserProfile(u.copy(approvalStatus = newStatus)) { success ->
                                if (success) {
                                    firebaseManager.logAuditAction("User Management", "Toggle User Status", u.email, u.approvalStatus, newStatus, currentUserEmail)
                                    Toast.makeText(context, "User status updated to $newStatus.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onResetPassword = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(u.email)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        firebaseManager.logAuditAction("User Management", "Password Reset Email", u.email, "", "SENT", currentUserEmail)
                                        Toast.makeText(context, "Password reset email sent to ${u.email}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        },
                        onDelete = { userToDelete = u }
                    )
                }
            }
        }
    }

    userToDelete?.let { u ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User Account (${u.name})") },
            text = { Text("Are you sure you want to delete user account '${u.email}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        firebaseManager.deleteUser(u.uid) { success ->
                            if (success) {
                                firebaseManager.logAuditAction("User Management", "Delete User Account", u.email)
                                Toast.makeText(context, "User ${u.name} deleted.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Delete User") }
            },
            dismissButton = { TextButton(onClick = { userToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminUserCard(
    user: UserProfile,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onChangeRole: () -> Unit,
    onChangeBlock: () -> Unit,
    onToggleStatus: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${user.email} • Role: ${user.role.uppercase()}", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = when (user.approvalStatus.uppercase()) { "APPROVED" -> Color(0xFFECFDF5); "REJECTED" -> Color(0xFFFEF2F2); else -> Color(0xFFFFFBEB) },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(user.approvalStatus, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = when (user.approvalStatus.uppercase()) { "APPROVED" -> Color(0xFF10B981); "REJECTED" -> Color(0xFFDC2626); else -> Color(0xFFD97706) })
                }
            }
            Text("Company: ${user.company.ifEmpty { "Mahindra" }} | Block: ${user.block.ifEmpty { "V-Block" }}", fontSize = 11.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (user.approvalStatus.equals("PENDING", ignoreCase = true)) {
                    Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) {
                        Text("Approve", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onReject, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)), modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) {
                        Text("Reject", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(onClick = onResetPassword, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) {
                        Text("Reset Pass", fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)), modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) {
                        Text("Delete User", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCompanyBlocksView(
    firebaseManager: FirebaseManager,
    originalTools: List<OriginalTool>,
    companyName: String,
    currentAdminUid: String
) {
    val context = LocalContext.current
    var companyMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var showAddBlockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        firebaseManager.getMasterCompanyList { map -> companyMap = map }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Facility Company & Blocks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            Button(onClick = { showAddBlockDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        companyMap.forEach { (comp, blocks) ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Company: $comp", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))
                    Text("Registered Blocks: ${blocks.joinToString()}", fontSize = 12.sp, color = Color(0xFF475569))
                }
            }
        }
    }

    if (showAddBlockDialog) {
        var newBlockName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            title = { Text("Add Block to $companyName", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newBlockName,
                    onValueChange = { newBlockName = it },
                    label = { Text("New Block Name (e.g. S-Block / Assembly-Line-2)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanBlock = newBlockName.trim()
                        if (cleanBlock.isNotBlank()) {
                            firebaseManager.addBlockToCompany(companyName, cleanBlock) { success, _ ->
                                if (success) {
                                    firebaseManager.logAuditAction("Admin Panel", "Add Block", companyName, "", cleanBlock)
                                    firebaseManager.getMasterCompanyList { map -> companyMap = map }
                                    Toast.makeText(context, "Block '$cleanBlock' added to $companyName!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showAddBlockDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) { Text("Save Block") }
            },
            dismissButton = { TextButton(onClick = { showAddBlockDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminToolsView(
    originalTools: List<OriginalTool>,
    backupTools: List<BackupTool>,
    firebaseManager: FirebaseManager,
    adminCompany: String,
    adminBlock: String
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var toolToDelete by remember { mutableStateOf<OriginalTool?>(null) }
    var backupToDelete by remember { mutableStateOf<BackupTool?>(null) }
    var editingOriginalTool by remember { mutableStateOf<OriginalTool?>(null) }
    var editingBackupTool by remember { mutableStateOf<BackupTool?>(null) }

    val filteredOriginals = remember(originalTools, searchQuery) {
        originalTools.filter {
            searchQuery.isBlank() ||
            it.toolId.contains(searchQuery, ignoreCase = true) ||
            it.toolName.contains(searchQuery, ignoreCase = true) ||
            it.line.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tools & Backup Tools Directory", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search tool ID, name, line...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Original Tools (${filteredOriginals.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))

        filteredOriginals.forEach { tool ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${tool.toolId} - ${tool.toolName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${tool.line} (${tool.station}) • Status: ${tool.status}", fontSize = 11.sp, color = Color.Gray)
                    }
                    Row {
                        IconButton(onClick = { editingOriginalTool = tool }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB))
                        }
                        IconButton(onClick = { toolToDelete = tool }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }

        Text("Backup Tools (${backupTools.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF059669))

        backupTools.forEach { bTool ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${bTool.toolId} (${bTool.model})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Location: ${bTool.location} • Status: ${bTool.status}", fontSize = 11.sp, color = Color.Gray)
                    }
                    Row {
                        IconButton(onClick = { editingBackupTool = bTool }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB))
                        }
                        IconButton(onClick = { backupToDelete = bTool }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }
    }

    if (editingOriginalTool != null) {
        OriginalToolDialog(
            tool = editingOriginalTool,
            existingToolList = originalTools,
            firebaseManager = firebaseManager,
            onDismiss = { editingOriginalTool = null },
            onSave = { updatedTool ->
                firebaseManager.saveOriginalTool(updatedTool)
                firebaseManager.logAuditAction("Admin Tool Management", "Edit Original Tool", updatedTool.toolId)
                editingOriginalTool = null
                Toast.makeText(context, "Tool ${updatedTool.toolId} updated successfully.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingBackupTool != null) {
        EditBackupToolDialog(
            tool = editingBackupTool!!,
            onDismiss = { editingBackupTool = null },
            onSave = { updated ->
                firebaseManager.saveBackupTool(updated)
                firebaseManager.logAuditAction("Admin Tool Management", "Edit Backup Tool", updated.toolId)
                editingBackupTool = null
                Toast.makeText(context, "Backup Tool ${updated.toolId} updated.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    toolToDelete?.let { tool ->
        AlertDialog(
            onDismissRequest = { toolToDelete = null },
            title = { Text("Delete Original Tool (${tool.toolId})") },
            text = { Text("Are you sure you want to delete '${tool.toolId}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        firebaseManager.deleteOriginalTool(tool.toolId, adminCompany, adminBlock)
                        firebaseManager.logAuditAction("Tool Management", "Delete Tool", tool.toolId)
                        toolToDelete = null
                        Toast.makeText(context, "Tool ${tool.toolId} deleted.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toolToDelete = null }) { Text("Cancel") } }
        )
    }

    backupToDelete?.let { bTool ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = { Text("Delete Backup Tool (${bTool.toolId})") },
            text = { Text("Are you sure you want to delete backup tool '${bTool.toolId}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        firebaseManager.deleteBackupTool(bTool.toolId, adminCompany, adminBlock)
                        firebaseManager.logAuditAction("Tool Management", "Delete Backup Tool", bTool.toolId)
                        backupToDelete = null
                        Toast.makeText(context, "Backup tool ${bTool.toolId} deleted.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { backupToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminPmView(
    pmEntries: List<PmEntry>,
    originalTools: List<OriginalTool>,
    firebaseManager: FirebaseManager
) {
    val context = LocalContext.current
    var editingPmTask by remember { mutableStateOf<PmEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PM Maintenance Tasks (${pmEntries.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
        pmEntries.forEach { pm ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${pm.toolName} (${pm.toolId})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Scheduled: ${pm.scheduledDate} • Status: ${pm.status}", fontSize = 11.sp, color = if (pm.status == "Done") Color(0xFF10B981) else Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    }
                    Row {
                        IconButton(onClick = { editingPmTask = pm }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Task", tint = Color(0xFF2563EB))
                        }
                        IconButton(onClick = {
                            firebaseManager.deletePmEntry(pm.pmId, pm.company, pm.block)
                            firebaseManager.logAuditAction("PM Management", "Delete PM Task", pm.pmId)
                            Toast.makeText(context, "PM task deleted.", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }
    }

    editingPmTask?.let { pm ->
        var schedDate by remember { mutableStateOf(pm.scheduledDate) }
        var status by remember { mutableStateOf(pm.status) }
        var cycleMonths by remember { mutableStateOf(pm.pmCycleMonths.toString()) }

        AlertDialog(
            onDismissRequest = { editingPmTask = null },
            title = { Text("Edit PM Task (${pm.toolId})", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = schedDate, onValueChange = { schedDate = it }, label = { Text("Scheduled Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = cycleMonths, onValueChange = { cycleMonths = it }, label = { Text("Cycle Months (Default: 6)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Status (Pending / In_Progress / Done)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cMonths = cycleMonths.toIntOrNull() ?: 6
                        val updatedPm = pm.copy(scheduledDate = schedDate.trim(), status = status.trim(), pmCycleMonths = cMonths)
                        firebaseManager.savePmEntry(updatedPm)
                        firebaseManager.logAuditAction("PM Management", "Edit PM Task", pm.pmId)
                        editingPmTask = null
                        Toast.makeText(context, "PM Task for ${pm.toolId} updated.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) { Text("Save Changes") }
            },
            dismissButton = { TextButton(onClick = { editingPmTask = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminBreakdownView(
    breakdowns: List<BreakdownEntry>,
    firebaseManager: FirebaseManager,
    adminCompany: String,
    adminBlock: String
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Breakdown Entries (${breakdowns.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
        breakdowns.forEach { bd ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tool: ${bd.originalToolId} (${bd.line} - ${bd.station})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Problem: ${bd.problem}", fontSize = 12.sp, color = Color.DarkGray)
                        Text("Status: ${bd.status}", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        firebaseManager.deleteBreakdownEntry(bd.entryId, adminCompany, adminBlock)
                        firebaseManager.logAuditAction("Breakdown", "Delete Entry", bd.entryId)
                        Toast.makeText(context, "Breakdown entry deleted.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStockView(
    products: List<Product>,
    firebaseManager: FirebaseManager,
    adminCompany: String,
    adminBlock: String
) {
    val context = LocalContext.current
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Inventory Spare Parts (${products.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
        products.forEach { prod ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prod.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Part No: ${prod.productCode} • Stock: ${prod.currentStock} Nos • Min: ${prod.minStockLevel}", fontSize = 11.sp, color = Color.Gray)
                        Text("Price: ₹${prod.unitPrice} • Model: ${prod.toolId.ifEmpty { "All Models" }}", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                    }
                    Row {
                        IconButton(onClick = { editingProduct = prod }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Part", tint = Color(0xFF2563EB))
                        }
                        IconButton(onClick = {
                            firebaseManager.deleteProduct(prod.productCode, adminCompany, adminBlock)
                            firebaseManager.logAuditAction("Inventory", "Delete Part", prod.productCode)
                            Toast.makeText(context, "Spare part deleted.", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }
    }

    editingProduct?.let { prod ->
        var partName by remember { mutableStateOf(prod.productName) }
        var currentStockStr by remember { mutableStateOf(prod.currentStock.toString()) }
        var minStockStr by remember { mutableStateOf(prod.minStockLevel.toString()) }
        var unitPriceStr by remember { mutableStateOf(prod.unitPrice.toString()) }
        var category by remember { mutableStateOf(prod.category) }
        var compatibleModel by remember { mutableStateOf(prod.toolId) }

        AlertDialog(
            onDismissRequest = { editingProduct = null },
            title = { Text("Edit Spare Part (${prod.productCode})", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = partName, onValueChange = { partName = it }, label = { Text("Part Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = currentStockStr, onValueChange = { currentStockStr = it }, label = { Text("Current Stock") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = minStockStr, onValueChange = { minStockStr = it }, label = { Text("Min Stock") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = unitPriceStr, onValueChange = { unitPriceStr = it }, label = { Text("Unit Price (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = compatibleModel, onValueChange = { compatibleModel = it }, label = { Text("Compatible Tool Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cStock = currentStockStr.toIntOrNull() ?: prod.currentStock
                        val mStock = minStockStr.toIntOrNull() ?: prod.minStockLevel
                        val price = unitPriceStr.toDoubleOrNull() ?: prod.unitPrice

                        val updatedProd = prod.copy(
                            productName = partName.trim(),
                            currentStock = cStock,
                            minStockLevel = mStock,
                            unitPrice = price,
                            category = category.trim(),
                            toolId = compatibleModel.trim(),
                            company = adminCompany,
                            block = adminBlock
                        )
                        firebaseManager.saveProduct(updatedProd)
                        firebaseManager.logAuditAction("Inventory", "Edit Spare Part", prod.productCode)
                        editingProduct = null
                        Toast.makeText(context, "Part ${prod.productCode} updated.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) { Text("Save Changes") }
            },
            dismissButton = { TextButton(onClick = { editingProduct = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminSettingsView(
    firebaseManager: FirebaseManager,
    adminCompany: String,
    adminBlock: String,
    userEmail: String
) {
    val context = LocalContext.current
    var showChangePassDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Admin Setup & Security Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Facility Configuration", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                HorizontalDivider()
                Text("Active Company: $adminCompany", fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                Text("Active Shop / Block: $adminBlock", fontSize = 13.sp, color = Color(0xFF475569))
                Text("Logged Admin: $userEmail", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security & Password Setup", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                HorizontalDivider()
                Text("Update Master Admin Authentication Password or send Password Reset Email.", fontSize = 12.sp, color = Color.Gray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showChangePassDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Update Master Pass")
                    }

                    OutlinedButton(
                        onClick = {
                            if (userEmail.isNotBlank()) {
                                com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(userEmail)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            Toast.makeText(context, "Password reset email sent to $userEmail", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Reset email failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Send Reset Email")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FCM Topic Broadcasts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                HorizontalDivider()
                Text("Active Topics: topic_company_${FirebaseManager.getCompanyId(adminCompany)}, topic_block_${FirebaseManager.getCompanyId(adminCompany)}_${FirebaseManager.getBlockId(adminBlock)}", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        ToolCareFirebaseMessagingService.subscribeUserScopedTopics(adminCompany, adminBlock, "admin")
                        Toast.makeText(context, "Re-subscribed to FCM Topics for $adminCompany - $adminBlock", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("Refresh FCM Subscriptions")
                }
            }
        }
    }

    if (showChangePassDialog) {
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePassDialog = false },
            title = { Text("Update Master Admin Password", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Admin Password *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.length >= 6) {
                            firebaseManager.updateAdminPassword(newPass.trim())
                            firebaseManager.logAuditAction("Admin Setup", "Update Admin Password", "ADMIN_PASS")
                            Toast.makeText(context, "Admin Master Password updated successfully.", Toast.LENGTH_SHORT).show()
                            showChangePassDialog = false
                        } else {
                            Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Update")
                }
            },
            dismissButton = { TextButton(onClick = { showChangePassDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminGenericModuleView(
    moduleKey: String,
    firebaseManager: FirebaseManager,
    adminCompany: String,
    adminBlock: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Module: $moduleKey", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
            Text("Facility: $adminCompany • Block: $adminBlock", fontSize = 12.sp, color = Color(0xFF2563EB))
            HorizontalDivider()
            Text("Module $moduleKey is active and configured for $adminCompany ($adminBlock). All actions write real-time audit logs.", fontSize = 12.sp, color = Color(0xFF475569))
        }
    }
}
