package com.example.toolcare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale

private fun downloadSampleTemplate(context: Context) {
    try {
        val sampleCsv = StringBuilder()
        sampleCsv.append("Tool ID,Tool Name,Line,Station,Make,Model,Serial Number,Torque (Nm),RPM Speed,Fitment / Assembly,Install Date\n")
        sampleCsv.append("T-001,Bosch Cordless Drill,Line-1,ST-02,Bosch,EXACT ION 2-700,SN-88231920,12,2700,Engine Cover,2024-01-15\n")
        sampleCsv.append("T-002,Angle Nutrunner,Line-2,ST-05,Bosch,ANGLE EXACT 30,SN-99201244,25,1200,Transmission,2024-03-20\n")
        sampleCsv.append("T-003,Pneumatic Screwdriver,Line-1,ST-08,Bosch,CLEAN EXACT 15,SN-77210982,8,1500,Battery Pack,2024-05-10\n")

        val fileName = "ToolCare_Sample_Tools_Template.csv"
        val file = File(context.cacheDir, fileName)
        val out = FileOutputStream(file)
        out.write(sampleCsv.toString().toByteArray())
        out.close()

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Download Sample CSV Template").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
        Toast.makeText(context, "Sample CSV Template generated successfully!", Toast.LENGTH_SHORT).show()
    } catch (e: Throwable) {
        Log.e("OriginalToolsScreen", "Download template error", e)
        Toast.makeText(context, "Template Download Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun parseCsvLineQuoteAware(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    for (ch in line) {
        if (ch == '"') {
            inQuotes = !inQuotes
        } else if (ch == ',' && !inQuotes) {
            tokens.add(sb.toString().trim().removeSurrounding("\""))
            sb.clear()
        } else {
            sb.append(ch)
        }
    }
    tokens.add(sb.toString().trim().removeSurrounding("\""))
    return tokens
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginalToolsScreen(
    originalToolList: List<OriginalTool>,
    userRole: String,
    userCompany: String,
    userBlock: String,
    firebaseManager: FirebaseManager? = null,
    onAddOriginalTool: (OriginalTool) -> Unit,
    onToolClick: (OriginalTool) -> Unit,
    onBackClick: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTool by remember { mutableStateOf<OriginalTool?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    BackHandler { onBackClick() }

    val filteredTools = originalToolList.filter {
        it.toolId.contains(searchQuery, ignoreCase = true) ||
        it.toolName.contains(searchQuery, ignoreCase = true) ||
        it.model.contains(searchQuery, ignoreCase = true) ||
        it.serialNumber.contains(searchQuery, ignoreCase = true)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(fileUri) ?: return@let

                val aliases = mapOf(
                    "toolId" to listOf("Tool ID", "ID", "ToolId", "Tool_ID", "ToolID"),
                    "toolName" to listOf("Tool Name", "Name", "ToolName", "Description", "Tool_Name"),
                    "line" to listOf("Line", "Line Name"),
                    "station" to listOf("Station", "ST NO", "ST", "Station No", "StationNumber"),
                    "make" to listOf("Make", "Brand", "Manufacturer", "Tool Make"),
                    "model" to listOf("Model", "Tool Model", "ToolModel"),
                    "serialNumber" to listOf("Serial Number", "S/N", "Serial", "Sr. No.", "Sr.No.", "SerialNumber", "SN"),
                    "torque" to listOf("Torque (Nm)", "Torque", "TorqueNm", "Torque Value", "Torque NM"),
                    "rpm" to listOf("RPM Speed", "RPM", "Speed", "Tool Speed", "RpmSpeed"),
                    "fitment" to listOf("Fitment / Assembly", "Fitment", "FitmentAssembly", "Assembly", "Application", "M&M Application", "Process"),
                    "installDate" to listOf("Install Date", "Date", "Installation Date", "Install_Date")
                )

                fun cleanHeader(h: String) = h.trim().lowercase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "").replace("-", "")

                val reader = BufferedReader(InputStreamReader(inputStream))
                val headerLine = reader.readLine() ?: return@let
                val headers = parseCsvLineQuoteAware(headerLine)

                val mapping = aliases.mapValues { (_, valueList) ->
                    headers.indexOfFirst { h -> 
                        val cleanH = cleanHeader(h)
                        valueList.any { cleanHeader(it) == cleanH }
                    }
                }

                val toolIdIdx = mapping["toolId"] ?: -1
                if (toolIdIdx == -1) {
                    Toast.makeText(context, "Critical Column Missing: Tool ID\nFound: ${headers.joinToString(", ")}", Toast.LENGTH_LONG).show()
                    return@let
                }

                val existingToolIds = originalToolList.map { it.toolId.trim().uppercase(Locale.ROOT) }.toSet()
                var addedCount = 0
                var skippedCount = 0

                var lineText = reader.readLine()
                while (lineText != null) {
                    if (lineText.isNotBlank()) {
                        val tokens = parseCsvLineQuoteAware(lineText)
                        if (tokens.isNotEmpty()) {
                            val toolId = if (toolIdIdx >= 0 && toolIdIdx < tokens.size) tokens[toolIdIdx].trim() else ""
                            val cleanToolIdUpper = toolId.uppercase(Locale.ROOT)

                            if (toolId.isNotEmpty()) {
                                if (existingToolIds.contains(cleanToolIdUpper)) {
                                    skippedCount++
                                } else {
                                    fun getToken(key: String): String {
                                        val idx = mapping[key] ?: -1
                                        return if (idx >= 0 && idx < tokens.size) tokens[idx].trim() else ""
                                    }

                                    val fitmentVal = getToken("fitment")
                                    val newTool = OriginalTool(
                                        toolId = toolId,
                                        toolName = getToken("toolName").ifEmpty { "Tool $toolId" },
                                        make = getToken("make").ifEmpty { "Bosch" },
                                        line = getToken("line"),
                                        station = getToken("station"),
                                        model = getToken("model"),
                                        serialNumber = getToken("serialNumber"),
                                        torque = getToken("torque"),
                                        rpm = getToken("rpm"),
                                        fitment = fitmentVal,
                                        application = fitmentVal,
                                        installDate = getToken("installDate"),
                                        status = "OK",
                                        company = userCompany,
                                        block = userBlock
                                    )
                                    onAddOriginalTool(newTool)
                                    addedCount++
                                }
                            }
                        }
                    }
                    lineText = reader.readLine()
                }
                reader.close()
                Toast.makeText(context, "Import complete: $addedCount tools added, $skippedCount duplicate IDs skipped.", Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                Log.e("Import", "CSV Import Failed", e)
                Toast.makeText(context, "CSV Import Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.original_tools_list_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { downloadSampleTemplate(context) },
                        border = BorderStroke(1.dp, Color(0xFF2563EB)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sample CSV Template", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), fontSize = 12.sp)
                    }

                    FloatingActionButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "text/csv", "application/csv")) },
                        containerColor = Color(0xFF059669)
                    ) {
                        Text("Import CSV", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }

                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Tool")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Tool ID, Model, or S/N") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.total_original_tools_count, filteredTools.size),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.no_original_tools), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        filteredTools,
                        key = { tool -> if (tool.toolId.isNotEmpty()) tool.toolId else "orig_${tool.hashCode()}" }
                    ) { tool ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onToolClick(tool) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tool.toolId,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = tool.status,
                                        color = if (tool.status == "OK") Color(0xFF10B981) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    if (userRole.lowercase() in listOf("admin", "super_admin", "technician")) {
                                        IconButton(
                                            onClick = { 
                                                editingTool = tool
                                                showDialog = true 
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Tool",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                Text(text = stringResource(R.string.label_name, tool.toolName), fontSize = 14.sp)
                                Text(text = "Model: ${tool.model} • S/N: ${tool.serialNumber}", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                Text(text = stringResource(R.string.label_line_station, tool.line, tool.station), fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        if (showDialog) {
            OriginalToolDialog(
                tool = editingTool,
                existingToolList = originalToolList,
                firebaseManager = firebaseManager,
                onDismiss = { 
                    showDialog = false
                    editingTool = null 
                },
                onSave = { newTool ->
                    onAddOriginalTool(newTool)
                    showDialog = false
                    editingTool = null
                }
            )
        }
    }
}

@Composable
fun OriginalToolDialog(
    tool: OriginalTool? = null,
    existingToolList: List<OriginalTool> = emptyList(),
    firebaseManager: FirebaseManager? = null,
    onDismiss: () -> Unit,
    onSave: (OriginalTool) -> Unit
) {
    val context = LocalContext.current
    var toolId by remember { mutableStateOf(tool?.toolId ?: "") }
    var toolName by remember { mutableStateOf(tool?.toolName ?: "") }
    var line by remember { mutableStateOf(tool?.line ?: "") }
    var station by remember { mutableStateOf(tool?.station ?: "") }
    var model by remember { mutableStateOf(tool?.model ?: "Bosch EXACT ION 12-700") }
    var srNo by remember { mutableStateOf(tool?.serialNumber ?: "") }
    var torque by remember { mutableStateOf(tool?.torque ?: "") }
    var rpm by remember { mutableStateOf(tool?.rpm ?: "") }
    var fitment by remember { mutableStateOf(tool?.fitment?.ifEmpty { tool.application } ?: "") }
    var installDate by remember { mutableStateOf(tool?.installDate ?: "") }
    var status by remember { mutableStateOf(tool?.status ?: "OK") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tool == null) stringResource(R.string.add_original_tool_title) else "Edit Original Tool (${tool.toolId})") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = toolId,
                    onValueChange = { toolId = it },
                    label = { Text(stringResource(R.string.label_tool_id_hint)) },
                    enabled = tool == null
                )
                OutlinedTextField(
                    value = toolName,
                    onValueChange = { toolName = it },
                    label = { Text(stringResource(R.string.label_tool_name_hint)) }
                )
                OutlinedTextField(
                    value = "Bosch",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Make / Brand (Restricted to Bosch)") }
                )
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it },
                    label = { Text(stringResource(R.string.label_line_hint)) }
                )
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text(stringResource(R.string.label_station_hint)) }
                )
                
                if (tool != null) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = { status = it },
                        label = { Text("Status (OK / Breakdown)") }
                    )
                }

                HorizontalDivider()
                Text("Manufacturer & Parameter Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
                OutlinedTextField(value = srNo, onValueChange = { srNo = it }, label = { Text("Serial Number") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = torque, onValueChange = { torque = it }, label = { Text("Torque (Nm)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rpm, onValueChange = { rpm = it }, label = { Text("RPM Speed") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = fitment, onValueChange = { fitment = it }, label = { Text("Fitment / Assembly Name") })
                OutlinedTextField(value = installDate, onValueChange = { installDate = it }, label = { Text("Install Date (YYYY-MM-DD)") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanId = toolId.trim()
                    if (cleanId.isBlank() || toolName.isBlank()) {
                        Toast.makeText(context, "Please fill in mandatory Tool ID and Tool Name fields.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (tool == null) {
                        // Creating New Tool: Check Duplicate ID
                        if (existingToolList.any { it.toolId.trim().equals(cleanId, ignoreCase = true) }) {
                            Toast.makeText(context, "⚠️ Tool ID '$cleanId' already exists. Duplicate IDs not allowed.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        onSave(OriginalTool(
                            toolId = cleanId, 
                            toolName = toolName.trim(), 
                            make = "Bosch",
                            line = line.trim(), 
                            station = station.trim(),
                            model = model.trim(),
                            serialNumber = srNo.trim(),
                            torque = torque.trim(),
                            rpm = rpm.trim(),
                            application = fitment.trim(),
                            fitment = fitment.trim(),
                            installDate = installDate.trim(),
                            status = status.trim()
                        ))
                    } else {
                        // Editing Existing Tool: In-Place Partial Update
                        val fieldsMap = mapOf<String, Any>(
                            "toolName" to toolName.trim(),
                            "make" to "Bosch",
                            "line" to line.trim(),
                            "station" to station.trim(),
                            "model" to model.trim(),
                            "serialNumber" to srNo.trim(),
                            "torque" to torque.trim(),
                            "rpm" to rpm.trim(),
                            "application" to fitment.trim(),
                            "fitment" to fitment.trim(),
                            "installDate" to installDate.trim(),
                            "status" to status.trim()
                        )
                        firebaseManager?.updateOriginalToolFields(tool.toolId, tool.company, tool.block, fieldsMap)
                        onDismiss()
                        Toast.makeText(context, "Tool ${tool.toolId} updated successfully.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(if (tool == null) stringResource(R.string.btn_save) else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
