package com.example.toolcare

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDocumentsScreen(
    tool: OriginalTool? = null,
    userRole: String = "technician",
    company: String = "",
    block: String = "",
    firebaseManager: FirebaseManager? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val toolIdStr = tool?.toolId ?: "T-025"
    val modelStr = tool?.model ?: "EXACT ION 12-700"
    val userCompany = if (company.isNotBlank()) company else tool?.company.orEmpty()
    val userBlock = if (block.isNotBlank()) block else tool?.block.orEmpty()

    // Real-Time Firebase Stream
    val firebaseDocs by firebaseManager?.getToolTechnicalDocuments(userCompany, userBlock, toolIdStr)?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // Mock initial documents fallback if Firebase node is empty
    val initialDocs = remember(toolIdStr) {
        listOf(
            TechnicalDocument(
                id = "doc_00",
                toolId = toolIdStr,
                title = "Assembly Line Layout & Electrical Schematic",
                category = "Line Diagram",
                description = "Line wiring diagram, station power layout, and controller interlock schematic for $toolIdStr.",
                fileType = "PDF",
                pages = "2 Pages",
                fileSize = "1.5 MB",
                updatedDate = "01 Oct 2026",
                url = "https://drive.google.com/file/d/sample_line_diagram/view",
                visibleTo = "All Users",
                storageProvider = "GOOGLE_DRIVE"
            ),
            TechnicalDocument(
                id = "doc_01",
                toolId = toolIdStr,
                title = "Exploded View & Spare Parts Catalogue",
                category = "Diagrams",
                description = "Complete assembly diagram, part numbers, and torque specifications for $toolIdStr.",
                fileType = "PDF",
                pages = "12 Pages",
                fileSize = "3.2 MB",
                updatedDate = "28 Sep 2026",
                url = "https://drive.google.com/file/d/sample_exploded_view/view",
                visibleTo = "All Users",
                storageProvider = "GOOGLE_DRIVE"
            ),
            TechnicalDocument(
                id = "doc_02",
                toolId = toolIdStr,
                title = "Calibration Certificate (NABL Accredited)",
                category = "Certificates",
                description = "Official NABL calibration compliance certificate valid until October 2027.",
                fileType = "PDF",
                pages = "4 Pages",
                fileSize = "1.8 MB",
                updatedDate = "15 Sep 2026",
                url = "https://drive.google.com/file/d/sample_calibration_cert/view",
                visibleTo = "All Users",
                storageProvider = "GOOGLE_DRIVE"
            ),
            TechnicalDocument(
                id = "doc_03",
                toolId = toolIdStr,
                title = "Standard Operating Procedure (SOP - Preventive Maintenance)",
                category = "SOP",
                description = "Step-by-step PM inspection checklist and safety compliance guide.",
                fileType = "PDF",
                pages = "6 Pages",
                fileSize = "1.2 MB",
                updatedDate = "01 Aug 2026",
                url = "https://drive.google.com/file/d/sample_sop_document/view",
                visibleTo = "All Users",
                storageProvider = "GOOGLE_DRIVE"
            ),
            TechnicalDocument(
                id = "doc_04",
                toolId = toolIdStr,
                title = "Bosch Technical Datasheet & Torque Curves",
                category = "Technical Datasheet",
                description = "Factory motor performance specifications, battery life curves, and RPM limits.",
                fileType = "PDF",
                pages = "8 Pages",
                fileSize = "2.4 MB",
                updatedDate = "10 Jul 2026",
                url = "https://drive.google.com/file/d/sample_technical_datasheet/view",
                visibleTo = "All Users",
                storageProvider = "GOOGLE_DRIVE"
            ),
            TechnicalDocument(
                id = "doc_05",
                toolId = toolIdStr,
                title = "Operator Service & Maintenance Manual",
                category = "Manuals",
                description = "Official Bosch user guide for routine cleaning, grease renewal, and fault codes.",
                fileType = "PDF",
                pages = "24 Pages",
                fileSize = "5.6 MB",
                updatedDate = "12 May 2026",
                url = "https://drive.google.com/file/d/sample_operator_manual/view",
                visibleTo = "All Users",
                storageProvider = "GOOGLE_DRIVE"
            )
        )
    }

    val displayDocs = remember(firebaseDocs, initialDocs) {
        if (firebaseDocs.isNotEmpty()) firebaseDocs else initialDocs
    }

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDocDialog by remember { mutableStateOf(false) }

    var viewingDocument by remember { mutableStateOf<TechnicalDocument?>(null) }
    var editingDoc by remember { mutableStateOf<TechnicalDocument?>(null) }
    var deletingDoc by remember { mutableStateOf<TechnicalDocument?>(null) }

    val categoriesList = listOf("All", "Line Diagram", "Diagrams", "Certificates", "SOP", "Technical Datasheet", "Manuals")

    val filteredDocs = remember(displayDocs, selectedCategoryFilter, searchQuery) {
        displayDocs.filter { doc ->
            val matchesCategory = selectedCategoryFilter == "All" || doc.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    doc.title.contains(searchQuery, ignoreCase = true) ||
                    doc.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Documents & Technical Library", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$toolIdStr • $modelStr", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (userRole.isNotBlank() && userRole.lowercase() != "viewer") {
                        Button(
                            onClick = { showAddDocDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Document", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (userRole.isNotBlank() && userRole.lowercase() != "viewer") {
                ExtendedFloatingActionButton(
                    onClick = { showAddDocDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Document") },
                    text = { Text("Upload Document", fontWeight = FontWeight.Bold) },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bosch Technical Library Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bosch Technical Library", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Exploded views, certificates, SOPs & datasheets", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    BoschLogo()
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search document title or description...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Category Chips List
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categoriesList) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Text("Technical Library (${filteredDocs.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

            // Document Cards List
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No technical documents found for $toolIdStr.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    items(filteredDocs, key = { doc -> if (doc.id.isNotEmpty()) doc.id else "doc_${doc.hashCode()}" }) { doc ->
                        TechnicalDocumentCard(
                            doc = doc,
                            userRole = userRole,
                            onOpen = { viewingDocument = doc },
                            onEdit = { editingDoc = doc },
                            onReplace = { editingDoc = doc },
                            onDelete = { deletingDoc = doc }
                        )
                    }
                }
            }
        }
    }

    // In-App Document Viewer Dialog
    viewingDocument?.let { doc ->
        InAppDocumentViewerDialog(
            doc = doc,
            toolId = toolIdStr,
            model = modelStr,
            onDismiss = { viewingDocument = null },
            onOpenInDrive = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(doc.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Admin / Tech Add Document Dialog
    if (showAddDocDialog) {
        AddTechnicalDocumentDialog(
            toolId = toolIdStr,
            company = userCompany,
            block = userBlock,
            onDismiss = { showAddDocDialog = false },
            onSave = { newDoc ->
                firebaseManager?.saveTechnicalDocument(newDoc)
                showAddDocDialog = false
                Toast.makeText(context, "Document '${newDoc.title}' added successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Admin Delete Confirmation Dialog
    deletingDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { deletingDoc = null },
            title = { Text("Delete Document (${doc.title})", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove '${doc.title}' from $toolIdStr's technical library?") },
            confirmButton = {
                Button(
                    onClick = {
                        firebaseManager?.deleteTechnicalDocument(userCompany, userBlock, doc.toolId, doc.id)
                        deletingDoc = null
                        Toast.makeText(context, "Document deleted.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDoc = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TechnicalDocumentCard(
    doc: TechnicalDocument,
    userRole: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val categoryColor = when (doc.category.uppercase(Locale.ROOT)) {
        "LINE DIAGRAM", "LINE_DIAGRAM" -> Color(0xFF0284C7)
        "DIAGRAMS", "TOOL DRAWINGS" -> Color(0xFF2563EB)
        "CERTIFICATES" -> Color(0xFF10B981)
        "SOP" -> Color(0xFFD97706)
        "TECHNICAL DATASHEET" -> Color(0xFF7C3AED)
        else -> Color(0xFF0D9488)
    }

    val categoryIcon = when (doc.category.uppercase(Locale.ROOT)) {
        "LINE DIAGRAM", "LINE_DIAGRAM" -> Icons.Default.Place
        "DIAGRAMS", "TOOL DRAWINGS" -> Icons.Default.Edit
        "CERTIFICATES" -> Icons.Default.CheckCircle
        "SOP" -> Icons.Default.Build
        "TECHNICAL DATASHEET" -> Icons.Default.List
        else -> Icons.Default.Info
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Category Icon Box
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(categoryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = categoryIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = doc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Text(text = doc.description, fontSize = 12.sp, color = Color(0xFF64748B))

                    Spacer(modifier = Modifier.height(4.dp))

                    // Badges Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = doc.fileType,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = if (doc.storageProvider == "BACKBLAZE_B2") Color(0xFFEFF6FF) else Color(0xFFECFDF5),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (doc.storageProvider == "BACKBLAZE_B2") "B2 Storage" else "Google Drive",
                                color = if (doc.storageProvider == "BACKBLAZE_B2") Color(0xFF2563EB) else Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text("• ${doc.fileSize}", fontSize = 11.sp, color = Color(0xFF64748B))
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text("Updated: ${doc.updatedDate}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }

                if (userRole.equals("admin", ignoreCase = true) || userRole.equals("super admin", ignoreCase = true)) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color(0xFF64748B))
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Details") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Replace Document") },
                                onClick = {
                                    menuExpanded = false
                                    onReplace()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFDC2626)) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Text("OPEN DOCUMENT →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

fun generateSampleDocumentBitmap(
    context: android.content.Context,
    title: String,
    toolId: String,
    model: String,
    category: String,
    pageNumber: Int = 1
): Bitmap {
    val width = 1200
    val height = 1600
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val headerPaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#0F172A") }
    canvas.drawRect(0f, 0f, width.toFloat(), 120f, headerPaint)

    val headerTextPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 42f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText("BOSCH TECHNICAL LIBRARY", 50f, 75f, headerTextPaint)

    val redBadgePaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#E21A22") }
    canvas.drawRect((width - 250).toFloat(), 0f, width.toFloat(), 120f, redBadgePaint)

    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 44f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText(title.take(35).uppercase(Locale.ROOT), 50f, 220f, titlePaint)

    val subPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#2563EB")
        textSize = 32f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText("TOOL ID: $toolId  |  MODEL: $model  |  CATEGORY: $category", 50f, 280f, subPaint)

    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CBD5E1")
        strokeWidth = 4f
    }
    canvas.drawLine(50f, 320f, (width - 50).toFloat(), 320f, linePaint)

    val contentPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#334155")
        textSize = 28f
        isAntiAlias = true
    }
    canvas.drawText("Page $pageNumber - Native Content View & Assembly Overview", 50f, 380f, contentPaint)

    val boxPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F1F5F9")
        style = android.graphics.Paint.Style.FILL
    }
    val boxBorder = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#2563EB")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }

    canvas.drawRoundRect(50f, 420f, (width - 50).toFloat(), 1050f, 16f, 16f, boxPaint)
    canvas.drawRoundRect(50f, 420f, (width - 50).toFloat(), 1050f, 16f, 16f, boxBorder)

    val gridPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        strokeWidth = 2f
    }
    var y = 480f
    while (y < 1000f) {
        canvas.drawLine(70f, y, (width - 70).toFloat(), y, gridPaint)
        y += 80f
    }

    val gridText = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 26f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText("[1.0] Motor Assembly & Controller Interlock Interface", 80f, 520f, gridText)
    canvas.drawText("[2.0] Transducer Shut-off & Target Torque Setting: 12.00 Nm", 80f, 600f, gridText)
    canvas.drawText("[3.0] Battery Operating Voltage: 18.0 V DC (Lithium ION)", 80f, 680f, gridText)
    canvas.drawText("[4.0] Angle Gear Head Fitment & Lubrication Specification", 80f, 760f, gridText)
    canvas.drawText("[5.0] Calibrated Output Tolerance: ± 2.0% Accuracy", 80f, 840f, gridText)
    canvas.drawText("[6.0] Line Wiring Diagram & Station Power Interlock", 80f, 920f, gridText)

    val footerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#64748B")
        textSize = 24f
        isAntiAlias = true
    }
    canvas.drawText("ToolCare Factory Maintenance System  •  Confidential Technical Asset  •  Page $pageNumber", 50f, (height - 60).toFloat(), footerPaint)

    return bitmap
}

@Composable
fun InAppDocumentViewerDialog(
    doc: TechnicalDocument,
    toolId: String,
    model: String,
    onDismiss: () -> Unit,
    onOpenInDrive: () -> Unit
) {
    val context = LocalContext.current

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    var pdfPageIndex by remember { mutableIntStateOf(0) }
    var pdfTotalPages by remember { mutableIntStateOf(1) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val fileTypeUpper = doc.fileType.uppercase(Locale.ROOT)
    val isImage = fileTypeUpper == "IMAGE" || doc.contentType.contains("image") || doc.url.endsWith(".jpg") || doc.url.endsWith(".png") || doc.url.endsWith(".jpeg")
    val isPdf = fileTypeUpper == "PDF" || doc.contentType.contains("pdf") || doc.url.endsWith(".pdf") || doc.originalFileName.endsWith(".pdf") || !isImage

    LaunchedEffect(doc.url, pdfPageIndex, isPdf) {
        if (isPdf && doc.url.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(doc.url)
                    if (uri.scheme == "content" || uri.scheme == "file") {
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            val renderer = android.graphics.pdf.PdfRenderer(pfd)
                            pdfTotalPages = renderer.pageCount.coerceAtLeast(1)
                            val pageIdx = pdfPageIndex.coerceIn(0, pdfTotalPages - 1)
                            val page = renderer.openPage(pageIdx)

                            val bmp = Bitmap.createBitmap(
                                page.width * 2,
                                page.height * 2,
                                Bitmap.Config.ARGB_8888
                            )
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            renderer.close()
                            pfd.close()

                            withContext(Dispatchers.Main) {
                                pdfBitmap = bmp
                            }
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PdfRenderer", "Failed to render local PDF", e)
                }

                val sampleBmp = generateSampleDocumentBitmap(context, doc.title, toolId, model, doc.category, pdfPageIndex + 1)
                withContext(Dispatchers.Main) {
                    pdfTotalPages = 4
                    pdfBitmap = sampleBmp
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$toolId • $model (${if (isPdf) "Native PDF" else if (isImage) "Zoomable Image" else "Document Preview"})", fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4f) }) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                        }
                        IconButton(onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.75f) }) {
                            Icon(Icons.Default.Close, contentDescription = "Zoom Out", tint = Color.White)
                        }
                        IconButton(onClick = { zoomScale = 1f; panOffsetX = 0f; panOffsetY = 0f }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom", tint = Color.White)
                        }
                        IconButton(onClick = onOpenInDrive) {
                            Icon(Icons.Default.Share, contentDescription = "Open Link", tint = Color.White)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPdf && pdfBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        zoomScale = (zoomScale * zoom).coerceIn(0.8f, 4.0f)
                                        panOffsetX += pan.x
                                        panOffsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = pdfBitmap!!.asImageBitmap(),
                                contentDescription = "PDF Page ${pdfPageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = panOffsetX,
                                        translationY = panOffsetY
                                    )
                            )
                        }
                    } else if (isImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        zoomScale = (zoomScale * zoom).coerceIn(0.8f, 4.0f)
                                        panOffsetX += pan.x
                                        panOffsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val uri = remember(doc.url) { Uri.parse(doc.url) }
                            val imgBmp = remember(uri) {
                                try {
                                    if (uri.scheme == "content" || uri.scheme == "file") {
                                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                                        val bmp = BitmapFactory.decodeFileDescriptor(pfd?.fileDescriptor)
                                        pfd?.close()
                                        bmp
                                    } else null
                                } catch (_: Exception) { null }
                            }

                            if (imgBmp != null) {
                                Image(
                                    bitmap = imgBmp.asImageBitmap(),
                                    contentDescription = doc.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .graphicsLayer(
                                            scaleX = zoomScale,
                                            scaleY = zoomScale,
                                            translationX = panOffsetX,
                                            translationY = panOffsetY
                                        )
                                )
                            } else {
                                val sampleImage = generateSampleDocumentBitmap(context, doc.title, toolId, model, "Image Photo", 1)
                                Image(
                                    bitmap = sampleImage.asImageBitmap(),
                                    contentDescription = doc.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .graphicsLayer(
                                            scaleX = zoomScale,
                                            scaleY = zoomScale,
                                            translationX = panOffsetX,
                                            translationY = panOffsetY
                                        )
                                )
                            }
                        }
                    } else {
                        val webUrl = remember(doc.url) {
                            if (doc.url.startsWith("http://") || doc.url.startsWith("https://")) {
                                if (doc.url.contains("google.com/file") || doc.url.contains("drive.google.com")) {
                                    doc.url
                                } else {
                                    "https://docs.google.com/viewer?embedded=true&url=${Uri.encode(doc.url)}"
                                }
                            } else {
                                "https://drive.google.com"
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    webViewClient = WebViewClient()
                                    loadUrl(webUrl)
                                }
                            },
                            update = { webView -> webView.loadUrl(webUrl) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (isPdf) {
                    Surface(
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (pdfPageIndex > 0) pdfPageIndex-- },
                                enabled = pdfPageIndex > 0
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Page", tint = if (pdfPageIndex > 0) Color.White else Color.Gray)
                            }

                            Text(
                                text = "Page ${pdfPageIndex + 1} of $pdfTotalPages",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            IconButton(
                                onClick = { if (pdfPageIndex < pdfTotalPages - 1) pdfPageIndex++ },
                                enabled = pdfPageIndex < pdfTotalPages - 1
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Next Page", tint = if (pdfPageIndex < pdfTotalPages - 1) Color.White else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTechnicalDocumentDialog(
    toolId: String,
    company: String = "",
    block: String = "",
    onDismiss: () -> Unit,
    onSave: (TechnicalDocument) -> Unit
) {
    val context = LocalContext.current
    val targetComp = company.ifBlank { "Mahindra" }
    val targetBlk = block.ifBlank { "V-Block" }

    var docName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Diagrams") }
    var descriptionInput by remember { mutableStateOf("") }
    var uploadMode by remember { mutableStateOf("FILE") } // "FILE" or "DRIVE"
    var driveUrlInput by remember { mutableStateOf("") }
    var visibilityOption by remember { mutableStateOf("All Users") }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSizeFormatted by remember { mutableStateOf("") }
    var selectedSizeBytes by remember { mutableLongStateOf(0L) }
    var selectedMimeType by remember { mutableStateOf("application/pdf") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val helper = B2StorageHelper(context)
            when (val validation = helper.validatePreflightUpload(fileUri)) {
                is B2UploadValidationResult.Valid -> {
                    selectedFileUri = fileUri
                    selectedFileName = validation.filename
                    selectedFileSizeFormatted = validation.formattedSize
                    selectedSizeBytes = validation.sizeBytes
                    selectedMimeType = validation.contentType
                    if (docName.isBlank()) {
                        docName = validation.filename.substringBeforeLast(".")
                    }
                    Toast.makeText(context, "Selected ${validation.filename} (${validation.formattedSize})", Toast.LENGTH_SHORT).show()
                }
                is B2UploadValidationResult.Error -> {
                    Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val helper = B2StorageHelper(context)
            when (val validation = helper.validatePreflightUpload(fileUri)) {
                is B2UploadValidationResult.Valid -> {
                    selectedFileUri = fileUri
                    selectedFileName = if (validation.filename.isNotBlank()) validation.filename else "photo_${System.currentTimeMillis()}.jpg"
                    selectedFileSizeFormatted = validation.formattedSize
                    selectedSizeBytes = validation.sizeBytes
                    selectedMimeType = if (validation.contentType.isBlank()) "image/jpeg" else validation.contentType
                    if (docName.isBlank()) {
                        docName = "Photo $toolId"
                    }
                    Toast.makeText(context, "Selected Image (${validation.formattedSize})", Toast.LENGTH_SHORT).show()
                }
                is B2UploadValidationResult.Error -> {
                    Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    val categoryOptions = listOf("Diagrams", "Certificates", "SOP", "Technical Datasheet", "Manuals")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Technical Document", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text("Document Name *") },
                    placeholder = { Text("Enter document name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Document Type *") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { categoryDropdownExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                    )
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        categoryOptions.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Description") },
                    placeholder = { Text("Enter short description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Text("Upload File / Google Drive Link *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { uploadMode = "FILE" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (uploadMode == "FILE") Color(0xFFEFF6FF) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Direct File / Photo", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { uploadMode = "DRIVE" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (uploadMode == "DRIVE") Color(0xFFEFF6FF) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Drive Link", fontSize = 11.sp)
                    }
                }

                if (uploadMode == "FILE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*", "application/pdf")) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Choose File / PDF", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery / Photo", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    if (selectedFileUri != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(selectedFileName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    Text("Size: $selectedFileSizeFormatted • Type: $selectedMimeType", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = driveUrlInput,
                        onValueChange = { driveUrlInput = it },
                        label = { Text("Google Drive Share Link") },
                        placeholder = { Text("https://drive.google.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Text("Visible To:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = visibilityOption == "All Users",
                        onClick = { visibilityOption = "All Users" }
                    )
                    Text("All Users", fontSize = 13.sp)

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = visibilityOption == "Admin Only",
                        onClick = { visibilityOption = "Admin Only" }
                    )
                    Text("Admin Only", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (docName.isNotBlank()) {
                        val isB2 = uploadMode == "FILE" && selectedFileUri != null
                        val objKey = if (isB2) {
                            B2StorageConfig.buildObjectKey(targetComp, targetBlk, "documents", toolId, selectedFileName)
                        } else ""

                        val newDoc = TechnicalDocument(
                            toolId = toolId,
                            title = docName.trim(),
                            category = selectedCategory,
                            description = descriptionInput.ifEmpty { "Technical document for $toolId" },
                            fileType = if (isB2) (if (selectedMimeType.contains("image")) "IMAGE" else if (selectedMimeType.contains("pdf")) "PDF" else "DOC") else "PDF",
                            fileSize = if (isB2) selectedFileSizeFormatted else "2.1 MB",
                            updatedDate = "Today",
                            url = if (isB2) selectedFileUri.toString() else driveUrlInput.ifEmpty { "https://drive.google.com/drive/my-drive" },
                            visibleTo = visibilityOption,
                            storageProvider = if (isB2) "BACKBLAZE_B2" else "GOOGLE_DRIVE",
                            objectKey = objKey,
                            originalFileName = selectedFileName,
                            contentType = selectedMimeType,
                            sizeBytes = selectedSizeBytes,
                            companyId = targetComp,
                            blockId = targetBlk
                        )
                        onSave(newDoc)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Text("SAVE DOCUMENT", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.Gray) }
        }
    )
}

@Composable
fun BoschLogo(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Surface(
            color = Color(0xFFE21A22),
            shape = CircleShape,
            modifier = Modifier.size(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("BOSCH", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
    }
}
