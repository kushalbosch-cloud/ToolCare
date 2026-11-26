package com.example.toolcare

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    firebaseManager: FirebaseManager,
    company: String,
    block: String,
    originalTools: List<OriginalTool>,
    backupTools: List<BackupTool>,
    breakdownEntries: List<BreakdownEntry>,
    pmEntries: List<PmEntry>,
    products: List<Product>,
    userRole: String = "technician",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    isProcessing = true
                    val inputStream = context.contentResolver.openInputStream(fileUri)
                    if (inputStream != null) {
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val jsonString = reader.use { it.readText() }
                        val success = BackupRestoreHelper.importRestoreBackup(context, jsonString, firebaseManager)
                        isProcessing = false
                        if (success) {
                            Toast.makeText(context, "Database restored successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to parse or restore backup file.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        isProcessing = false
                        Toast.makeText(context, "Could not open selected backup file.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    isProcessing = false
                    Log.e("BackupRestore", "Restore error", e)
                    Toast.makeText(context, "Restore Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore Data", fontWeight = FontWeight.Bold) },
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
            Text("Database Management", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text("Safely export and back up all tool inventories, breakdown records, PM logs, and history, or restore from a previously exported backup file.", fontSize = 13.sp, color = Color(0xFF64748B))

            // Workspace Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active Workspace", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Divider(color = Color(0xFFF1F5F9))
                    SpecRow("Company", company.ifEmpty { "Mahindra" })
                    SpecRow("Shop / Block", block.ifEmpty { "V-Block" })
                    SpecRow("Original Tools Count", originalTools.size.toString())
                    SpecRow("Backup Tools Count", backupTools.size.toString())
                    SpecRow("Breakdown Records Count", breakdownEntries.size.toString())
                    SpecRow("PM Entries Count", pmEntries.size.toString())
                }
            }

            // Export Backup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Export Workspace Backup", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Text("Export all tool inventories, maintenance logs, and breakdown history into a secure JSON backup file.", fontSize = 12.sp, color = Color(0xFF64748B))

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                BackupRestoreHelper.exportWorkspaceBackup(
                                    context = context,
                                    company = company.ifEmpty { "Mahindra" },
                                    block = block.ifEmpty { "V-Block" },
                                    originalTools = originalTools,
                                    backupTools = backupTools,
                                    breakdownEntries = breakdownEntries,
                                    pmEntries = pmEntries,
                                    products = products
                                )
                                isProcessing = false
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Export Backup File (.json)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Import Restore Card (Restricted strictly to Admin)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Restore Database from Backup", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    if (userRole == "admin") {
                        Text("Select a previously exported JSON backup file from your device storage to restore database records.", fontSize = 12.sp, color = Color(0xFF64748B))

                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Select & Restore Backup File", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Text("Restricted Access: Database import & restore is restricted strictly to Administrator level.", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Admin Access Required", fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
