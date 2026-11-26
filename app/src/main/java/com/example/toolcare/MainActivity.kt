package com.example.toolcare

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.painterResource
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ToolStatus {
    const val OK = "OK"
    const val AVAILABLE = "Available"
    const val AVAILABLE_BACKUP = "Available Backup"
    const val IN_USE = "In Use"
    const val BREAKDOWN = "Breakdown"
    const val UNDER_REPAIR = "Under Repair"
    const val REPAIRED_PENDING_HANDOVER = "Repaired (Pending Handover)"
    const val HANDOVER_COMPLETED = "Handover Completed"
    const val MISSING = "Missing"

    fun isAvailableBackup(status: String?): Boolean {
        val s = (status ?: "").trim().lowercase(Locale.ROOT)
        return s == "available" || s == "available backup" || s == "ok"
    }

    fun parsePmDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val formats = arrayOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd", "dd MMM yyyy")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                sdf.isLenient = false
                return sdf.parse(dateStr.trim())
            } catch (_: Exception) {}
        }
        return null
    }

    fun isPmDue(scheduledDateStr: String?, statusStr: String?, todayStr: String): Boolean {
        if (scheduledDateStr.isNullOrBlank()) return false
        val s = (statusStr ?: "").trim().lowercase(Locale.ROOT)
        if (s == "completed" || s == "done" || s == "pass") return false

        val d = parsePmDate(scheduledDateStr) ?: return false
        val calScheduled = Calendar.getInstance().apply {
            time = d
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calToday = Calendar.getInstance().apply {
            parsePmDate(todayStr)?.let { time = it }
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return !calScheduled.after(calToday)
    }
}

class MainActivity : ComponentActivity() {
    private val pendingDeepLinkScreen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PermissionRequester()
                    AppNavigation(pendingDeepLinkScreen = pendingDeepLinkScreen)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val targetScreen = intent?.getStringExtra("screen")
        if (!targetScreen.isNullOrBlank()) {
            pendingDeepLinkScreen.value = targetScreen
        }
    }
}

@Composable
fun PermissionRequester() {
    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionsToRequest = remember {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        list.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notGranted = permissions.entries.filter { !it.value }
        if (notGranted.isNotEmpty()) {
            showRationaleDialog = true
        }
    }

    LaunchedEffect(Unit) {
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            launcher.launch(missingPermissions.toTypedArray())
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ToolCare Permissions Required", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ToolCare requires runtime permissions to operate essential factory maintenance functions:", fontSize = 13.sp)
                    Text("• Notifications: Broadcast alerts for tool breakdowns & PM tasks across devices.", fontSize = 12.sp, color = Color(0xFF475569))
                    Text("• Camera: Instant QR & Barcode scanning for tool identification.", fontSize = 12.sp, color = Color(0xFF475569))
                    Text("• Media Storage: Attach manuals, export reports, and upload profile pictures.", fontSize = 12.sp, color = Color(0xFF475569))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            launcher.launch(permissionsToRequest)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Open Settings", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Dismiss", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun RejectedApprovalScreen(
    userProfile: UserProfile,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF2F2))
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(56.dp))
                Text("रजिस्ट्रेशन नाकारले आहे", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF0F172A))
                Text("तुमचा अकाऊंट नोंदणी अर्ज प्रशासकाद्वारे नाकारला गेला आहे. कृपया अधिक माहितीसाठी ॲडमिन टीमशी संपर्क साधा.", fontSize = 13.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavigation(pendingDeepLinkScreen: MutableState<String?>? = null) {
    val appContext = LocalContext.current.applicationContext as ToolCareApplication
    val firebaseManager = remember { appContext.firebaseManager }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("toolcare_prefs", Context.MODE_PRIVATE) }
    val offlineSyncManager = remember(context) { OfflineSyncManager.getInstance(context) }

    // Auth & Navigation Stack State
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var currentScreen by remember { mutableStateOf(if (currentUser != null) "Home" else "Login") }
    val navigationStack = remember { mutableStateListOf<String>() }

    // Handle deep links from FCM Push Notification taps
    val activityContext = LocalContext.current as? ComponentActivity
    LaunchedEffect(activityContext) {
        val launchIntent = activityContext?.intent
        val targetScreenExtra = launchIntent?.getStringExtra("screen")
        val targetBlockExtra = launchIntent?.getStringExtra("blockId")
        if (!targetBlockExtra.isNullOrBlank()) {
            sharedPrefs.edit().putString("active_block_id", targetBlockExtra).apply()
        }
        if (!targetScreenExtra.isNullOrBlank()) {
            currentScreen = targetScreenExtra
            launchIntent?.removeExtra("screen")
        }
    }

    LaunchedEffect(pendingDeepLinkScreen?.value) {
        val target = pendingDeepLinkScreen?.value
        if (!target.isNullOrBlank()) {
            currentScreen = target
            pendingDeepLinkScreen.value = null
        }
    }

    fun navigateTo(targetScreen: String) {
        if (currentScreen != targetScreen) {
            navigationStack.add(currentScreen)
            currentScreen = targetScreen
        }
    }

    fun navigateBack() {
        if (navigationStack.isNotEmpty()) {
            currentScreen = navigationStack.removeAt(navigationStack.size - 1)
        } else if (currentScreen != "Home" && currentScreen != "Login") {
            currentScreen = "Home"
        }
    }

    // Real-Time Profile Synchronization
    val realtimeProfile by remember(currentUser?.uid) {
        val uid = currentUser?.uid
        if (!uid.isNullOrBlank()) firebaseManager.getUserProfileFlow(uid).catch { emit(null) }
        else flowOf(null)
    }.collectAsState(initial = null)

    LaunchedEffect(currentUser, realtimeProfile) {
        val user = currentUser
        if (user != null && realtimeProfile == null) {
            firebaseManager.getUserProfile(user.uid) { existingProfile ->
                if (existingProfile == null) {
                    val defaultProfile = UserProfile(
                        uid = user.uid,
                        name = user.displayName?.ifEmpty { user.email?.substringBefore("@") } ?: "User",
                        email = user.email ?: "",
                        role = "operator",
                        company = "Mahindra",
                        block = "vblock_001"
                    )
                    firebaseManager.saveUserProfile(defaultProfile)
                }
            }
        }
    }

    val userProfile = realtimeProfile

    val company = userProfile?.company.orEmpty()
    val userBlock = userProfile?.block.orEmpty()
    val userBlockId = FirebaseManager.getBlockId(userBlock)
    val userRole = userProfile?.role.orEmpty().lowercase(Locale.ROOT)

    val canSwitchBlocks = userRole in listOf("admin", "super admin", "super_admin", "technician")

    val initialBlockId = remember(company, userBlockId) {
        val saved = sharedPrefs.getString("active_block_id_${company}", null)
        if (!saved.isNullOrBlank()) saved else userBlockId
    }

    var activeBlockId by remember(company, userBlockId, canSwitchBlocks) {
        mutableStateOf(if (canSwitchBlocks) initialBlockId else userBlockId)
    }

    // Save activeBlockId to SharedPreferences when changed
    LaunchedEffect(activeBlockId, company) {
        if (company.isNotBlank() && activeBlockId.isNotBlank()) {
            sharedPrefs.edit().putString("active_block_id_${company}", activeBlockId).apply()
        }
    }

    // Guard: Do NOT invoke database flows if user is not logged in or profile is incomplete
    val isLoggedInAndConfigured = currentUser != null && company.isNotBlank() && activeBlockId.isNotBlank()

    // Flow streams protected by isLoggedInAndConfigured guard and scoped to (company, activeBlockId)
    val originalToolList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getOriginalTools(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val backupToolList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getBackupTools(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val breakdownList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getBreakdownEntries(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val pmEntries by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getPmEntries(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val pmHistoryList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getPmHistory(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val calibrationList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getCalibrationEntries(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val productList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getProducts(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val notificationsList by remember(isLoggedInAndConfigured, company, activeBlockId) {
        if (isLoggedInAndConfigured) firebaseManager.getNotifications(company, activeBlockId).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val usersList by remember(isLoggedInAndConfigured, company) {
        if (isLoggedInAndConfigured) firebaseManager.getAllUsers().catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val liveUnreadNotifCount = remember(notificationsList) { notificationsList.count { !it.isRead } }

    var activeShopTopic by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser?.uid, company, activeBlockId) {
        ToolCareFirebaseMessagingService.subscribeToTopics()
        if (company.isNotBlank() && activeBlockId.isNotBlank()) {
            val newShopTopic = "${company}_${activeBlockId}".lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
            val currentTopic = activeShopTopic
            if (currentTopic != null && currentTopic != newShopTopic) {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(currentTopic)
                } catch (_: Exception) {}
            }
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(newShopTopic)
            } catch (_: Exception) {}
            activeShopTopic = newShopTopic
        }
        val uid = currentUser?.uid
        if (!uid.isNullOrBlank()) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (!token.isNullOrBlank()) {
                        firebaseManager.updateFcmToken(uid, token)
                    }
                }
            }
        }
    }

    // Admin Session State
    var isAdminAuthenticated by remember { mutableStateOf(false) }
    
    // Tool Selection & Search States
    var selectedToolForDetails by remember { mutableStateOf<OriginalTool?>(null) }
    var selectedToolForHistory by remember { mutableStateOf<OriginalTool?>(null) }
    var toolToEditInHistory by remember { mutableStateOf<OriginalTool?>(null) }
    var showManualSearchDialog by remember { mutableStateOf(false) }

    // Real-Time Tool Counters (Derived from live non-hold active breakdowns)
    val nonHoldBreakdowns = remember(breakdownList) {
        breakdownList.filter { entry ->
            !entry.entryId.startsWith("BD_AUTO_") &&
            !entry.status.contains("HOLD", ignoreCase = true) &&
            !entry.problem.contains("Verification Failed", ignoreCase = true)
        }
    }

    val activeBreakdownsTotal = remember(nonHoldBreakdowns) {
        nonHoldBreakdowns.count { it.status != ToolStatus.HANDOVER_COMPLETED && it.status != "Handover Completed" && it.status != "Closed" }
    }

    val repairCount = remember(nonHoldBreakdowns) {
        nonHoldBreakdowns.count { 
            it.status == ToolStatus.UNDER_REPAIR || 
            it.status == ToolStatus.REPAIRED_PENDING_HANDOVER || 
            it.status == "IN REPAIR" || 
            it.status == "Repaired (Pending Handover)"
        }
    }

    val reportedBreakdownCount = (activeBreakdownsTotal - repairCount).coerceAtLeast(0)

    val totalToolsCount = originalToolList.size
    val okToolsCount = (totalToolsCount - activeBreakdownsTotal).coerceAtLeast(0)

    // Auto-sync OriginalTool status: If an OriginalTool is marked Breakdown/Under Repair but has NO active breakdown, reset to OK
    LaunchedEffect(originalToolList, nonHoldBreakdowns, company, activeBlockId) {
        if (company.isNotBlank() && activeBlockId.isNotBlank() && originalToolList.isNotEmpty()) {
            val activeBreakdownToolIds = nonHoldBreakdowns
                .filter { it.status != ToolStatus.HANDOVER_COMPLETED && it.status != "Handover Completed" && it.status != "Closed" }
                .map { it.originalToolId.trim().uppercase(Locale.ROOT) }
                .toSet()

            originalToolList.forEach { tool ->
                val tidUpper = tool.toolId.trim().uppercase(Locale.ROOT)
                val statusUpper = tool.status.trim().uppercase(Locale.ROOT)
                val isMarkedDown = statusUpper == "BREAKDOWN" || statusUpper == "UNDER REPAIR" || statusUpper == "IN REPAIR"
                
                if (isMarkedDown && !activeBreakdownToolIds.contains(tidUpper)) {
                    firebaseManager.updateOriginalToolStatus(tool.toolId, company, activeBlockId, ToolStatus.OK)
                }
            }
        }
    }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val uniquePmDueTools = pmEntries.filter { ToolStatus.isPmDue(it.scheduledDate, it.status, todayStr) && !it.toolId.isNullOrEmpty() }
        .map { it.toolId.trim().uppercase(Locale.ROOT) }
        .distinct()
        .size
        .coerceAtMost(totalToolsCount)

    val availableBackupToolsCount = backupToolList.count { ToolStatus.isAvailableBackup(it.status) }

    var showProfileSettings by remember { mutableStateOf(false) }

    val scannerOptions = remember { QrScannerHelper.allBarcodeScannerOptions }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }
    val activity = context as? ComponentActivity
    var showExitToast by remember { mutableStateOf(false) }
    var isScanningTool by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun startQrCodeScan() {
        if (isScanningTool) return
        isScanningTool = true

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                isScanningTool = false
                val rawScanned = barcode.rawValue.orEmpty()
                if (rawScanned.isNotBlank()) {
                    val cleanScanned = QrScannerHelper.extractCleanToolId(rawScanned)
                    val targetToolId = if (cleanScanned.isNotBlank()) cleanScanned else rawScanned.trim().uppercase(Locale.ROOT)

                    var match = QrScannerHelper.findMatchingTool(
                        cleanScanned = targetToolId,
                        rawScanned = rawScanned,
                        toolList = originalToolList,
                        backupList = backupToolList
                    )

                    if (match == null) {
                        match = QrScannerHelper.resolveMahindraVBlockTool(
                            cleanScanned = targetToolId,
                            company = company,
                            block = activeBlockId
                        )
                        if (match != null) {
                            firebaseManager.saveOriginalTool(match)
                        }
                    }

                    if (match != null) {
                        selectedToolForDetails = match
                        navigateTo("Tool Details")
                        Toast.makeText(context, "Tool Found: ${match.toolId} (${match.toolName})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Scanned Code: '$targetToolId' (No tool record found)", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Scanned barcode is empty", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnCanceledListener {
                isScanningTool = false
            }
            .addOnFailureListener { e ->
                isScanningTool = false
                Log.e("MainActivity", "Scan error", e)
                Toast.makeText(context, "Scan error: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        bottomBar = {
            val userApprovalStatus = userProfile?.approvalStatus?.uppercase(Locale.ROOT)
            val isApproved = userApprovalStatus != "PENDING" && userApprovalStatus != "REJECTED"
            if (currentUser != null && userProfile != null && isApproved && currentScreen != "Login" && currentScreen != "Register") {
                ToolCareBottomBar(
                    currentRoute = currentScreen,
                    onTabSelected = { route ->
                        if (route == "Scan QR") {
                            startQrCodeScan()
                        } else {
                            navigateTo(route)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            val userApprovalStatus = userProfile?.approvalStatus?.uppercase(Locale.ROOT)

            if (currentUser != null && userProfile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2563EB))
                }
            } else if (currentUser != null && userApprovalStatus == "PENDING" && userProfile != null) {
                PendingApprovalScreen(
                    userProfile = userProfile,
                    onSignOut = {
                        auth.signOut()
                        currentUser = null
                        currentScreen = "Login"
                    }
                )
            } else if (currentUser != null && userApprovalStatus == "REJECTED" && userProfile != null) {
                RejectedApprovalScreen(
                    userProfile = userProfile,
                    onSignOut = {
                        auth.signOut()
                        currentUser = null
                        currentScreen = "Login"
                    }
                )
            } else {
                when (currentScreen) {
                    "Login" -> {
                        BackHandler { activity?.finish() }
                        LoginScreen(
                            firebaseManager = firebaseManager,
                            onLoginSuccess = { 
                                currentUser = auth.currentUser
                                navigationStack.clear()
                                currentScreen = "Home" 
                            },
                            onNavigateToRegister = { navigateTo("Register") }
                        )
                    }

                "Register" -> {
                    BackHandler { navigateBack() }
                    RegisterScreen(
                        firebaseManager = firebaseManager,
                        onRegisterSuccess = { 
                            currentUser = auth.currentUser
                            navigationStack.clear()
                            currentScreen = "Home" 
                        },
                        onNavigateToLogin = { navigateBack() }
                    )
                }

                "Home" -> {
                    BackHandler {
                        if (navigationStack.isNotEmpty()) {
                            navigateBack()
                        } else {
                            if (showExitToast) {
                                activity?.finish()
                            } else {
                                showExitToast = true
                                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    delay(2000)
                                    showExitToast = false
                                }
                            }
                        }
                    }
                    ToolCareHome(
                        userName = userProfile?.name?.ifEmpty { "User" } ?: "User",
                        userPhotoUrl = userProfile?.profilePhotoUrl,
                        totalTools = totalToolsCount,
                        okTools = okToolsCount,
                        breakdownTools = reportedBreakdownCount,
                        repairTools = repairCount,
                        pmDueTools = uniquePmDueTools,
                        availableBackupTools = availableBackupToolsCount,
                        userRole = userProfile?.role ?: "technician",
                        company = company,
                        activeBlockId = activeBlockId,
                        canSwitchBlocks = canSwitchBlocks,
                        firebaseManager = firebaseManager,
                        originalToolList = originalToolList,
                        backupToolList = backupToolList,
                        breakdownList = breakdownList,
                        pmEntries = pmEntries,
                        pmHistoryList = pmHistoryList,
                        productList = productList,
                        usersList = usersList,
                        onBlockSelected = { newBlockId, blockName ->
                            activeBlockId = newBlockId
                            navigationStack.clear()
                            selectedToolForDetails = null
                            selectedToolForHistory = null
                            toolToEditInHistory = null
                            currentScreen = "Home"
                            Toast.makeText(context, "Switched to $blockName", Toast.LENGTH_SHORT).show()
                        },
                        unreadNotificationCount = liveUnreadNotifCount,
                        onNotificationClick = { navigateTo("Notification Center") },
                        onNavigate = { screen -> navigateTo(screen) },
                        onScanQrClick = { startQrCodeScan() },
                        onSearchToolClick = { showManualSearchDialog = true },
                        onReportBreakdownClick = { navigateTo("Breakdown") },
                        onBreakdownClick = { navigateTo("Breakdown") },
                        onProfileClick = { showProfileSettings = true },
                        onAdminLongPress = {
                            navigateTo("Admin Panel")
                        },
                        onLogout = {
                            auth.signOut()
                            currentUser = null
                            navigationStack.clear()
                            currentScreen = "Login"
                            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        }
                    )

                    val currentProfile = userProfile
                    if (showProfileSettings && currentProfile != null) {
                        ProfileSettingsDialog(
                            userProfile = currentProfile,
                            firebaseManager = firebaseManager,
                            onDismiss = { showProfileSettings = false },
                            onSave = { updatedProfile ->
                                firebaseManager.saveUserProfile(updatedProfile)
                                showProfileSettings = false
                            }
                        )
                    }

                    if (showManualSearchDialog) {
                        ManualSearchDialog(
                            originalToolList = originalToolList,
                            onDismiss = { showManualSearchDialog = false },
                            onToolSelected = { tool ->
                                selectedToolForDetails = tool
                                showManualSearchDialog = false
                                navigateTo("Tool Details")
                            }
                        )
                    }
                }

                "Tools Menu" -> {
                    ToolsMenuScreen(
                        breakdownCount = activeBreakdownsTotal,
                        onNavigate = { screen -> navigateTo(screen) },
                        onBackClick = { navigateBack() }
                    )
                }

                "Scan QR", "Tool Details" -> {
                    ToolDetailsScreen(
                        tool = selectedToolForDetails ?: originalToolList.firstOrNull(),
                        onNavigate = { screen -> navigateTo(screen) },
                        onBackClick = { navigateBack() }
                    )
                }

                "Tool Master" -> {
                    ToolMasterScreen(
                        tool = selectedToolForDetails ?: originalToolList.firstOrNull(),
                        originalToolList = originalToolList,
                        breakdownList = breakdownList,
                        pmEntries = pmEntries,
                        calibrationList = calibrationList,
                        userRole = userProfile?.role ?: "technician",
                        firebaseManager = firebaseManager,
                        onNavigate = { screen -> navigateTo(screen) },
                        onBackClick = { navigateBack() }
                    )
                }

                "Calibration" -> {
                    CalibrationScreen(
                        tool = selectedToolForDetails ?: originalToolList.firstOrNull(),
                        calibrationList = calibrationList,
                        toolsList = originalToolList,
                        onSaveCalibration = { entry ->
                            firebaseManager.saveCalibrationEntry(entry)
                        },
                        onNavigateToHistory = {
                            navigateTo("History")
                        },
                        onBackClick = { navigateBack() }
                    )
                }

                "Notification Center" -> {
                    NotificationCenterScreen(
                        firebaseManager = firebaseManager,
                        company = company,
                        block = activeBlockId,
                        userRole = userProfile?.role ?: "technician",
                        onNavigateBack = { navigateBack() },
                        onNavigateToRoute = { route, entityIdStr ->
                            when (route.lowercase(Locale.ROOT)) {
                                "pm" -> navigateTo("PM Management")
                                "breakdown", "repair" -> navigateTo("Breakdown")
                                "backup" -> navigateTo("Backup Tools")
                                "stock" -> navigateTo("Spare Parts")
                                "admin" -> navigateTo("Admin Panel")
                                else -> {
                                    if (entityIdStr.isNotBlank()) {
                                        originalToolList.find { it.toolId.equals(entityIdStr, ignoreCase = true) }?.let {
                                            selectedToolForDetails = it
                                        }
                                    }
                                    navigateTo("Tool Details")
                                }
                            }
                        }
                    )
                }

                "Reports Dashboard" -> {
                    ReportsDashboardScreen(
                        originalToolList = originalToolList,
                        backupToolList = backupToolList,
                        breakdownList = breakdownList,
                        pmEntries = pmEntries,
                        pmHistoryList = pmHistoryList,
                        productList = productList,
                        onBackClick = { navigateBack() }
                    )
                }

                "Original Tools" -> {
                    OriginalToolsScreen(
                        originalToolList = originalToolList,
                        userRole = userProfile?.role ?: "technician",
                        userCompany = company,
                        userBlock = activeBlockId,
                        onAddOriginalTool = { newTool ->
                            firebaseManager.saveOriginalTool(newTool.copy(company = company, block = activeBlockId))
                        },
                        onToolClick = { tool ->
                            selectedToolForHistory = tool
                            selectedToolForDetails = tool
                            navigateTo("Tool Details")
                        },
                        onBackClick = { navigateBack() }
                    )
                }

                "Tool History Card" -> {
                    val targetHistoryTool = selectedToolForHistory ?: selectedToolForDetails ?: originalToolList.firstOrNull()
                    if (targetHistoryTool != null) {
                        ToolHistoryCardScreen(
                            tool = targetHistoryTool,
                            breakdownList = breakdownList,
                            pmEntries = pmEntries,
                            userRole = userProfile?.role ?: "technician",
                            onEditClick = { toolToEdit ->
                                toolToEditInHistory = toolToEdit
                            },
                            onBackClick = { navigateBack() }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navigateBack()
                        }
                    }
                    
                    toolToEditInHistory?.let { tool ->
                        OriginalToolDialog(
                            tool = tool,
                            existingToolList = originalToolList,
                            firebaseManager = firebaseManager,
                            onDismiss = { toolToEditInHistory = null },
                            onSave = { updatedTool ->
                                firebaseManager.saveOriginalTool(updatedTool.copy(company = company, block = activeBlockId))
                                toolToEditInHistory = null
                                selectedToolForHistory = updatedTool
                            }
                        )
                    }
                }

                "Backup Tools" -> {
                    BackupToolsScreen(
                        backupToolList = backupToolList,
                        firebaseManager = firebaseManager,
                        userRole = userProfile?.role ?: "technician",
                        userName = userProfile?.name?.ifEmpty { "Technician" } ?: "Technician",
                        onAddBackupTool = { newTool ->
                            firebaseManager.saveBackupTool(newTool.copy(company = company, block = activeBlockId, make = "Bosch"))
                        },
                        onUpdateBackupTool = { updatedTool ->
                            firebaseManager.saveBackupTool(updatedTool.copy(company = company, block = activeBlockId, make = "Bosch"))
                        },
                        onDeleteBackupTool = { toolToDelete ->
                            firebaseManager.deleteBackupTool(toolToDelete.toolId, company, activeBlockId)
                        },
                        onBackClick = { navigateBack() }
                    )
                }

                "Backup & Restore" -> {
                    BackupRestoreScreen(
                        firebaseManager = firebaseManager,
                        company = company,
                        block = activeBlockId,
                        originalTools = originalToolList,
                        backupTools = backupToolList,
                        breakdownEntries = breakdownList,
                        pmEntries = pmEntries,
                        products = productList,
                        userRole = userProfile?.role ?: "technician",
                        onBackClick = { navigateBack() }
                    )
                }

                "PM Management" -> {
                    PmScreen(
                        firebaseManager = firebaseManager,
                        originalToolList = originalToolList,
                        breakdownList = breakdownList,
                        productList = productList,
                        userRole = userProfile?.role ?: "technician",
                        userName = userProfile?.name?.ifEmpty { "Technician" } ?: "Technician",
                        company = company,
                        block = activeBlockId,
                        onBackClick = { navigateBack() }
                    )
                }

                "Breakdown" -> {
                    val notificationHelper = remember { NotificationHelper(context) }

                    BreakdownScreen(
                        backupToolList = backupToolList,
                        originalToolList = originalToolList,
                        breakdownEntries = breakdownList,
                        onSubmitBreakdown = { origId, backupId, lineStr, stationStr, prob ->
                            try {
                                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                                val newEntry = BreakdownEntry(
                                    originalToolId = origId,
                                    backupToolId = backupId ?: "No Backup Issued",
                                    line = lineStr,
                                    station = stationStr,
                                    problem = prob,
                                    timestamp = currentTime,
                                    company = company,
                                    block = activeBlockId,
                                    createdAt = System.currentTimeMillis(),
                                    reportedBy = userProfile?.name.orEmpty()
                                )

                                firebaseManager.updateOriginalToolStatus(origId, company, activeBlockId, ToolStatus.BREAKDOWN)

                                offlineSyncManager.saveBreakdownOffline(newEntry, firebaseManager) { success, msg ->
                                    try {
                                        if (success) {
                                            if (backupId != null && backupId != "No Backup Issued") {
                                                try {
                                                    firebaseManager.updateBackupToolStatus(backupId, company, activeBlockId, ToolStatus.IN_USE)
                                                    notificationHelper.triggerBackupAssignmentNotification(origId, backupId, "$company $activeBlockId")
                                                } catch (e: Exception) {
                                                    Log.e("MainActivity", "Error updating backup tool status/notification", e)
                                                }
                                            }
                                            try {
                                                notificationHelper.triggerBreakdownAlarm(origId, lineStr)
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Error triggering breakdown alarm", e)
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error handling saveBreakdownOffline callback", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error in onSubmitBreakdown", e)
                            }
                        },
                        onUpdateBackupTool = { updated -> firebaseManager.saveBackupTool(updated.copy(company = company, block = activeBlockId)) },
                        onBackClick = { navigateBack() }
                    )
                }

                "Repair" -> {
                    val notificationHelper = remember { NotificationHelper(context) }

                    RepairScreen(
                        breakdownList = breakdownList,
                        productList = productList,
                        onMarkRepaired = { entry, details, usedParts ->
                            val updatedEntry = entry.copy(
                                status = ToolStatus.REPAIRED_PENDING_HANDOVER,
                                repairDetails = details,
                                usedSpareParts = usedParts,
                                repairedAt = System.currentTimeMillis()
                            )
                            firebaseManager.saveBreakdownEntry(updatedEntry)

                            usedParts.filter { !it.isManualEntry }.forEach { part ->
                                firebaseManager.deductProductStock(part.partId, company, activeBlockId, part.quantityUsed)
                            }

                            notificationHelper.triggerBreakdownAlarm(
                                entry.originalToolId,
                                "${entry.line} (Repaired - Ready for Handover)"
                            )
                            Toast.makeText(context, "Tool ${entry.originalToolId} marked as Repaired! Ready for Handover.", Toast.LENGTH_SHORT).show()
                        },
                        onConfirmHandover = { entry, personName, shift ->
                            val updatedEntry = entry.copy(
                                status = ToolStatus.HANDOVER_COMPLETED,
                                handoverPerson = personName,
                                shift = shift,
                                handoverAt = System.currentTimeMillis()
                            )

                            firebaseManager.saveBreakdownEntry(updatedEntry)

                            if (entry.backupToolId.isNotBlank() && entry.backupToolId != "No Backup Issued") {
                                firebaseManager.updateBackupToolStatus(entry.backupToolId, company, activeBlockId, ToolStatus.AVAILABLE)
                            }

                            Toast.makeText(context, "Handover completed for Tool ${entry.originalToolId}. Status restored to OK.", Toast.LENGTH_SHORT).show()
                        },
                        onBackClick = { navigateBack() }
                    )
                }

                "Dashboard" -> {
                    DashboardScreen(
                        firebaseManager = firebaseManager,
                        originalToolList = originalToolList,
                        backupToolList = backupToolList,
                        breakdownList = breakdownList,
                        company = company,
                        block = activeBlockId,
                        userRole = userRole,
                        activeBlockFilter = activeBlockId,
                        onBlockFilterChange = { activeBlockId = it },
                        onBackClick = { navigateBack() }
                    )
                }

                "History" -> {
                    HistoryScreen(
                        tool = selectedToolForDetails,
                        breakdownList = breakdownList,
                        pmEntries = pmEntries,
                        calibrationList = calibrationList,
                        onBackClick = { navigateBack() }
                    )
                }

                "Spare Parts", "Stock Management" -> {
                    StockManagementScreen(
                        firebaseManager = firebaseManager,
                        productList = productList,
                        originalToolList = originalToolList,
                        tool = selectedToolForDetails,
                        userRole = userProfile?.role ?: "technician",
                        onBackClick = { navigateBack() }
                    )
                }

                "Parameter", "Parameter Settings" -> {
                    ParameterSettingsScreen(
                        tool = selectedToolForDetails ?: originalToolList.firstOrNull(),
                        userRole = userProfile?.role ?: "technician",
                        firebaseManager = firebaseManager,
                        onBackClick = { navigateBack() }
                    )
                }

                "Documents" -> {
                    ToolDocumentsScreen(
                        tool = selectedToolForDetails ?: originalToolList.firstOrNull(),
                        userRole = userProfile?.role ?: "technician",
                        firebaseManager = firebaseManager,
                        onBackClick = { navigateBack() }
                    )
                }

                "Issue / Return", "Tool Allocation" -> {
                    ToolAllocationScreen(
                        tool = selectedToolForDetails ?: originalToolList.firstOrNull(),
                        firebaseManager = firebaseManager,
                        userName = userProfile?.name?.ifEmpty { "Technician" } ?: "Technician",
                        onBackClick = { navigateBack() }
                    )
                }

                "Admin Panel" -> {
                    AdminPanelScreen(
                        userProfile = userProfile,
                        firebaseManager = firebaseManager,
                        originalTools = originalToolList,
                        backupTools = backupToolList,
                        breakdownEntries = breakdownList,
                        pmEntries = pmEntries,
                        products = productList,
                        onBackClick = { navigateBack() }
                    )
                }
            }
        }
    }
}
}

@Composable
fun UserProfileAvatar(photoUrl: String?, userName: String, size: Dp = 28.dp) {
    var loadedBitmap by remember(photoUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoUrl) {
        if (!photoUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    if (photoUrl.startsWith("data:image")) {
                        val base64Data = photoUrl.substringAfter("base64,")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        loadedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                        val url = java.net.URL(photoUrl)
                        val conn = url.openConnection()
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        val inputStream = conn.getInputStream()
                        loadedBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                    }
                } catch (_: Exception) {}
            }
        } else {
            loadedBitmap = null
        }
    }

    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap!!.asImageBitmap(),
            contentDescription = userName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Surface(
            shape = CircleShape,
            color = Color(0xFF2563EB),
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val initial = userName.trim().take(1).uppercase(Locale.ROOT).ifEmpty { "U" }
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp
                )
            }
        }
    }
}

@Composable
fun ProfileSettingsDialog(
    userProfile: UserProfile,
    firebaseManager: FirebaseManager,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(userProfile.name) }
    var mobile by remember { mutableStateOf(userProfile.mobile) }
    var department by remember { mutableStateOf(userProfile.department) }
    var designation by remember { mutableStateOf(userProfile.designation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User Profile Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = userProfile.email, onValueChange = {}, readOnly = true, enabled = false, label = { Text("Email (Read Only)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = "${userProfile.company} (${userProfile.block})", onValueChange = {}, readOnly = true, enabled = false, label = { Text("Company & Block") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(userProfile.copy(name = name, mobile = mobile, department = department, designation = designation))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) { Text("Save Profile") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ManualSearchDialog(
    originalToolList: List<OriginalTool>,
    onDismiss: () -> Unit,
    onToolSelected: (OriginalTool) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(originalToolList, query) {
        if (query.isBlank()) originalToolList
        else originalToolList.filter {
            it.toolId.contains(query, ignoreCase = true) ||
            it.toolName.contains(query, ignoreCase = true) ||
            it.line.contains(query, ignoreCase = true) ||
            it.station.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Factory Tool", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Enter Tool ID, Name, Line...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(filtered) { tool ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onToolSelected(tool) },
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${tool.toolId} - ${tool.toolName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${tool.line} (${tool.station}) • Status: ${tool.status}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun BlockSwitcherButton(
    company: String,
    activeBlockId: String,
    canSwitch: Boolean,
    firebaseManager: FirebaseManager,
    onBlockSelected: (newBlockId: String, blockName: String) -> Unit
) {
    if (!canSwitch || company.isBlank()) return

    val blocksList by remember(company) { firebaseManager.getBlocksForCompany(company) }.collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }

    val activeBlockName = remember(blocksList, activeBlockId) {
        blocksList.find { it.blockId.equals(activeBlockId, ignoreCase = true) }?.name
            ?: FirebaseManager.getBlockName(activeBlockId)
    }

    Box {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = activeBlockName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch Block", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "Switch Shop / Block (${FirebaseManager.getCompanyName(company)}):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            HorizontalDivider()

            blocksList.forEach { blockInfo ->
                val isSelected = blockInfo.blockId.equals(activeBlockId, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = blockInfo.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onBlockSelected(blockInfo.blockId, blockInfo.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeAttentionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomeMonthHighlightTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun formatElapsedDownTime(createdAtMs: Long, timestampStr: String): String {
    val ms = if (createdAtMs > 0L) {
        System.currentTimeMillis() - createdAtMs
    } else {
        val parsed = parseTimestampToMs(timestampStr)
        if (parsed > 0L) System.currentTimeMillis() - parsed else 0L
    }

    if (ms <= 0L) return "Just now"

    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60)) % 24
    val days = ms / (1000 * 60 * 60 * 24)

    return when {
        days > 0 -> "${days}d ${hours}h down"
        hours > 0 -> "${hours}h ${minutes}m down"
        minutes > 0 -> "${minutes}m down"
        else -> "Just now"
    }
}

fun parseTimestampToMs(timeStr: String): Long {
    if (timeStr.isBlank()) return 0L
    val formats = arrayOf(
        "dd/MM/yyyy HH:mm",
        "yyyy-MM-dd HH:mm",
        "dd MMM yyyy, HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "dd-MM-yyyy HH:mm"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            val date = sdf.parse(timeStr.trim())
            if (date != null) return date.time
        } catch (_: Exception) {}
    }
    return 0L
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCareHome(
    userName: String,
    userPhotoUrl: String? = null,
    totalTools: Int,
    okTools: Int,
    breakdownTools: Int,
    repairTools: Int,
    pmDueTools: Int,
    availableBackupTools: Int,
    userRole: String,
    company: String = "",
    activeBlockId: String = "",
    canSwitchBlocks: Boolean = false,
    firebaseManager: FirebaseManager? = null,
    originalToolList: List<OriginalTool> = emptyList(),
    backupToolList: List<BackupTool> = emptyList(),
    breakdownList: List<BreakdownEntry> = emptyList(),
    pmEntries: List<PmEntry> = emptyList(),
    pmHistoryList: List<PmHistoryEntry> = emptyList(),
    productList: List<Product> = emptyList(),
    usersList: List<UserProfile> = emptyList(),
    onBlockSelected: ((newBlockId: String, name: String) -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onScanQrClick: () -> Unit,
    onSearchToolClick: () -> Unit,
    onReportBreakdownClick: () -> Unit,
    onBreakdownClick: () -> Unit = {},
    onProfileClick: () -> Unit,
    onAdminLongPress: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showLogoutConfirmationDialog by remember { mutableStateOf(false) }

    val isOperator = userRole.equals("operator", ignoreCase = true)
    val isAdmin = userRole.equals("admin", ignoreCase = true) || userRole.equals("super admin", ignoreCase = true)

    // 1. "Down now" Section Filtered Data (Active Block & Status != Handover)
    val activeBlockBreakdowns = remember(breakdownList, activeBlockId) {
        breakdownList.filter { entry ->
            val matchesStatus = entry.status != "Handover Completed" && entry.status != "HANDOVER_COMPLETED"
            val matchesBlock = activeBlockId.isBlank() || activeBlockId == "ALL" || entry.block.isBlank() || entry.block.equals(activeBlockId, ignoreCase = true)
            matchesStatus && matchesBlock
        }.sortedBy { entry ->
            if (entry.createdAt > 0L) entry.createdAt else parseTimestampToMs(entry.timestamp)
        }
    }

    // 2. "Needs attention" Section Filtered Counts (Technician & Admin)
    val unverifiedBackupCount = remember(backupToolList, activeBlockId) {
        backupToolList.count { b ->
            val matchesBlock = activeBlockId.isBlank() || activeBlockId == "ALL" || b.block.isBlank() || b.block.equals(activeBlockId, ignoreCase = true)
            matchesBlock && !b.isVerifiedToday
        }
    }

    val pmAttentionCount = remember(pmEntries, activeBlockId) {
        pmEntries.count { p ->
            val matchesBlock = activeBlockId.isBlank() || activeBlockId == "ALL" || p.block.isBlank() || p.block.equals(activeBlockId, ignoreCase = true)
            matchesBlock && (p.status == "TODAY" || p.status == "OVERDUE" || p.status == "IN_PROGRESS")
        }
    }

    val lowStockCount = remember(productList, activeBlockId) {
        productList.count { prod ->
            val matchesBlock = activeBlockId.isBlank() || activeBlockId == "ALL" || prod.block.isBlank() || prod.block.equals(activeBlockId, ignoreCase = true)
            matchesBlock && (prod.currentStock <= prod.minStockLevel)
        }
    }

    val hasNeedsAttentionItems = !isOperator && (unverifiedBackupCount > 0 || pmAttentionCount > 0 || lowStockCount > 0)

    // 3. "This month" Section Filtered Metrics (Technician & Admin)
    val currentCal = remember { Calendar.getInstance() }
    val currentMonthIndex = currentCal.get(Calendar.MONTH)
    val currentYear = currentCal.get(Calendar.YEAR)

    val monthlyBreakdownsInBlock = remember(breakdownList, activeBlockId, currentMonthIndex, currentYear) {
        breakdownList.filter { entry ->
            val matchesBlock = activeBlockId.isBlank() || activeBlockId == "ALL" || entry.block.isBlank() || entry.block.equals(activeBlockId, ignoreCase = true)
            val matchesMonth = MISReportHelper.isDateInMonthAndYear(entry.timestamp, currentMonthIndex, currentYear)
            matchesBlock && matchesMonth
        }
    }

    val monthlyRepairedInBlock = remember(monthlyBreakdownsInBlock) {
        monthlyBreakdownsInBlock.filter { it.status == "Handover Completed" || it.status == "Repaired (Pending Handover)" }
    }

    val avgRepairTimeStr = remember(monthlyRepairedInBlock) {
        val repairDurationsMs = monthlyRepairedInBlock.mapNotNull { bd ->
            val startMs = if (bd.createdAt > 0L) bd.createdAt else parseTimestampToMs(bd.timestamp)
            val endMs = if (bd.handoverAt > 0L) bd.handoverAt else if (bd.repairedAt > 0L) bd.repairedAt else parseTimestampToMs(bd.timestamp)
            if (startMs > 0L && endMs > startMs) endMs - startMs else null
        }
        if (repairDurationsMs.isNotEmpty()) {
            val avgMs = repairDurationsMs.average().toLong()
            val mins = (avgMs / (1000 * 60)) % 60
            val hrs = avgMs / (1000 * 60 * 60)
            if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        } else "N/A"
    }

    val topBrokenToolId = remember(monthlyBreakdownsInBlock) {
        monthlyBreakdownsInBlock.groupBy { it.originalToolId }
            .maxByOrNull { it.value.size }
            ?.key ?: "None"
    }

    val hasThisMonthData = !isOperator && monthlyBreakdownsInBlock.isNotEmpty()

    // 4. Admin Pending Registration Requests Count
    val pendingUserCount = remember(usersList) {
        usersList.count { it.approvalStatus.equals("PENDING", ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Top Bar Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            Toast.makeText(context, "Hidden Admin Panel Triggered", Toast.LENGTH_SHORT).show()
                            onAdminLongPress()
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Menu",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text("TOOLCARE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(
                        "Maintenance Tool Management",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canSwitchBlocks && firebaseManager != null && onBlockSelected != null) {
                    BlockSwitcherButton(
                        company = company,
                        activeBlockId = activeBlockId,
                        canSwitch = canSwitchBlocks,
                        firebaseManager = firebaseManager,
                        onBlockSelected = onBlockSelected
                    )
                }

                Box {
                    IconButton(onClick = onNotificationClick) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications", tint = Color(0xFF0F172A))
                    }
                    if (unreadNotificationCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp),
                            color = Color(0xFFDC2626),
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (unreadNotificationCount > 9) "9+" else "$unreadNotificationCount",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = onProfileClick,
                        onLongClick = {
                            Toast.makeText(context, "Hidden Admin Authentication", Toast.LENGTH_SHORT).show()
                            onAdminLongPress()
                        }
                    )
                ) {
                    IconButton(onClick = onProfileClick) {
                        UserProfileAvatar(
                            photoUrl = userPhotoUrl,
                            userName = userName,
                            size = 28.dp
                        )
                    }
                }

                IconButton(onClick = { showLogoutConfirmationDialog = true }) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color(0xFFDC2626))
                }
            }
        }

        // Admin Pending Users Banner Alert
        if (isAdmin && pendingUserCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("Admin Panel") },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFD97706))
                        Column {
                            Text("Users Waiting for Approval ($pendingUserCount)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
                            Text("Tap to review and approve access requests in Admin Panel", fontSize = 11.sp, color = Color(0xFFB45309))
                        }
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                }
            }
        }

        // Dark Navy Greeting Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.65f)) {
                    Text(CalibrationUtils.getTimeBasedGreeting(), fontSize = 13.sp, color = Color(0xFF94A3B8))
                    Text(userName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Tool Management Team • ${FirebaseManager.getBlockName(activeBlockId)}", fontSize = 12.sp, color = Color(0xFF38BDF8))

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Right Tool", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Right Torque", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Better Production", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_drill_tool),
                    contentDescription = "Drill Tool",
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.CenterEnd)
                )

                Image(
                    painter = painterResource(id = R.drawable.ic_mahindra_logo),
                    contentDescription = "Company Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeActionButton("Scan Tool QR", Icons.Default.Search, Color(0xFF2563EB), Modifier.weight(1f)) {
                onScanQrClick()
            }
            HomeActionButton("Report Issue", Icons.Default.Warning, Color(0xFFDC2626), Modifier.weight(1f)) {
                onReportBreakdownClick()
            }
            HomeActionButton("Search Tool", Icons.Default.Search, Color(0xFF059669), Modifier.weight(1f)) {
                onSearchToolClick()
            }
        }

        // KPI Section Header
        Text(
            text = "FACTORY TOOL KPI DASHBOARD (${FirebaseManager.getBlockName(activeBlockId).uppercase(Locale.ROOT)})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
        )

        // KPI Summary Cards Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeKpiCard("TOTAL TOOLS", "$totalTools", Icons.Default.Build, Color(0xFF2563EB), Color(0xFFEFF6FF), Modifier.weight(1f)) {
                onNavigate("Tool Master")
            }
            HomeKpiCard("TOOL OK", "$okTools", Icons.Default.CheckCircle, Color(0xFF10B981), Color(0xFFECFDF5), Modifier.weight(1f)) {
                onNavigate("Original Tools")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeKpiCard("BREAKDOWN", "$breakdownTools", Icons.Default.Warning, Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.weight(1f)) {
                onNavigate("Breakdown")
            }
            HomeKpiCard("REPAIRING", "$repairTools", Icons.Default.Refresh, Color(0xFFD97706), Color(0xFFFFFBEB), Modifier.weight(1f)) {
                onNavigate("Repair")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeKpiCard("PM DUE", "$pmDueTools", Icons.Default.DateRange, Color(0xFF7C3AED), Color(0xFFF3E8FF), Modifier.weight(1f)) {
                onNavigate("PM Management")
            }
            HomeKpiCard("BACKUP AVAIL.", "$availableBackupTools", Icons.Default.Check, Color(0xFF059669), Color(0xFFECFDF5), Modifier.weight(1f)) {
                onNavigate("Backup Tools")
            }
        }

        DownNowSection(
            breakdowns = breakdownList,
            onOpen = { onBreakdownClick() },
            onSeeAll = { onBreakdownClick() }
        )

        // =========================================================================
        // 🔴 SECTION 1: "Down now" (Active Breakdowns - Sorted Oldest First)
        // =========================================================================
        if (activeBlockBreakdowns.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = "Down now", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Down now (${activeBlockBreakdowns.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                    }

                    TextButton(onClick = { onNavigate("Repair") }) {
                        Text("View Repair Screen →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                }

                activeBlockBreakdowns.take(3).forEach { entry ->
                    val elapsedText = formatElapsedDownTime(entry.createdAt, entry.timestamp)
                    val hasBackup = entry.backupToolId.isNotBlank() && entry.backupToolId != "No Backup Issued"
                    val backupText = if (hasBackup) "Backup Assigned: ${entry.backupToolId}" else "No backup issued"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("Repair") },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.originalToolId, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = elapsedText,
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text("Line ${entry.line} | Station ${entry.station}", fontSize = 12.sp, color = Color(0xFF475569))
                            Text("⚠️ ${entry.problem}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDC2626))
                            Text(
                                text = backupText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasBackup) Color(0xFF2563EB) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                if (activeBlockBreakdowns.size > 3) {
                    TextButton(
                        onClick = { onNavigate("Repair") },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "See all ${activeBlockBreakdowns.size} breakdowns →",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // ⚡ SECTION 2: "Needs attention" (Technician & Admin Roles Only)
        // =========================================================================
        if (hasNeedsAttentionItems) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Needs attention", tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Needs attention",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                if (unverifiedBackupCount > 0) {
                    HomeAttentionCard(
                        title = "$unverifiedBackupCount Backup Tools Need Daily Verification",
                        subtitle = "Tap to perform daily verification check",
                        icon = Icons.Default.Check,
                        color = Color(0xFF059669),
                        bgColor = Color(0xFFECFDF5)
                    ) {
                        onNavigate("Backup Tools")
                    }
                }

                if (pmAttentionCount > 0) {
                    HomeAttentionCard(
                        title = "$pmAttentionCount PM Tasks Due / Overdue",
                        subtitle = "Tap to view and execute scheduled PM inspections",
                        icon = Icons.Default.DateRange,
                        color = Color(0xFF7C3AED),
                        bgColor = Color(0xFFF3E8FF)
                    ) {
                        onNavigate("PM Management")
                    }
                }

                if (lowStockCount > 0) {
                    HomeAttentionCard(
                        title = "$lowStockCount Spare Parts Below Minimum Limit",
                        subtitle = "Tap to view low stock inventory items",
                        icon = Icons.Default.ShoppingCart,
                        color = Color(0xFFD97706),
                        bgColor = Color(0xFFFFFBEB)
                    ) {
                        onNavigate("Stock Management")
                    }
                }
            }
        }

        // =========================================================================
        // 📊 SECTION 3: "This month" (Technician & Admin Roles Only)
        // =========================================================================
        if (hasThisMonthData) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "This month", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                    Text(
                        text = "This month Highlights",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeMonthHighlightTile(
                        label = "Breakdowns",
                        value = "${monthlyBreakdownsInBlock.size}",
                        color = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f)
                    )
                    HomeMonthHighlightTile(
                        label = "Avg Repair Time",
                        value = avgRepairTimeStr,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f)
                    )
                    HomeMonthHighlightTile(
                        label = "Most Broken",
                        value = topBrokenToolId,
                        color = Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmationDialog = false },
            title = { Text("Logout Confirmation", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out of ToolCare?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmationDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmationDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        modifier = modifier.height(48.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun HomeKpiCard(
    label: String,
    count: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = count, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            }
            Surface(
                color = bgColor,
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
