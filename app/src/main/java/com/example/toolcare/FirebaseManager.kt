package com.example.toolcare

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.*

data class BlockInfo(
    val blockId: String = "",
    val name: String = "",
    val companyId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

class FirebaseManager private constructor() {
    private val db = FirebaseDatabase.getInstance("https://toolcare-65581-default-rtdb.asia-southeast1.firebasedatabase.app").reference

    init {
        initializeCompanyAndBlockStructure()
    }

    companion object {
        @Volatile
        private var INSTANCE: FirebaseManager? = null

        fun getInstance(): FirebaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseManager().also { INSTANCE = it }
            }
        }

        fun getCompanyId(companyNameOrId: String?): String {
            val clean = (companyNameOrId ?: "").trim().lowercase(Locale.ROOT)
            return when {
                clean == "mahindra_001" || clean == "mahindra" -> "mahindra_001"
                clean == "skoda_001" || clean == "skoda" -> "skoda_001"
                clean == "bosch_001" || clean == "bosch" -> "bosch_001"
                clean == "jsw_001" || clean == "jsw" -> "jsw_001"
                clean.isNotBlank() -> clean.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                else -> "mahindra_001"
            }
        }

        fun getCompanyName(companyNameOrId: String?): String {
            val clean = (companyNameOrId ?: "").trim().lowercase(Locale.ROOT)
            return when {
                clean == "mahindra_001" || clean == "mahindra" -> "Mahindra"
                clean == "skoda_001" || clean == "skoda" -> "Skoda"
                clean == "bosch_001" || clean == "bosch" -> "Bosch"
                clean == "jsw_001" || clean == "jsw" -> "JSW"
                clean.isNotBlank() -> companyNameOrId!!.trim()
                else -> "Mahindra"
            }
        }

        fun normalizeBlockId(blockInput: String?): String {
            if (blockInput.isNullOrBlank()) return "vblock_001"
            val clean = blockInput.trim().lowercase(Locale.ROOT)
            return when {
                clean == "vblock_001" || clean == "v-block" || clean == "v block" || clean == "v_block" || clean == "v" -> "vblock_001"
                clean == "qblock_001" || clean == "q-block" || clean == "q block" || clean == "q_block" || clean == "q" -> "qblock_001"
                clean == "sblock_001" || clean == "s-block" || clean == "s block" || clean == "s_block" || clean == "s" -> "sblock_001"
                clean == "tblock_001" || clean == "t-block" || clean == "t block" || clean == "t_block" || clean == "t" -> "tblock_001"
                clean == "e_block" || clean == "e-block" || clean == "e block" || clean == "e_block" || clean == "e" -> "e_block"
                clean == "rblock_001" || clean == "r-block" || clean == "r block" || clean == "r_block" || clean == "r" -> "rblock_001"
                clean == "pblock_001" || clean == "p-block" || clean == "p block" || clean == "p_block" || clean == "p" -> "pblock_001"
                clean == "d1block_001" || clean == "d1-block" || clean == "d1 block" || clean == "d1_block" || clean == "d1" -> "d1block_001"
                clean == "hblock_001" || clean == "h-block" || clean == "h block" || clean == "h_block" || clean == "h" -> "hblock_001"
                clean == "assembly_001" || clean == "assembly" || clean == "assembly-line-1" -> "assembly_001"
                clean == "paint_001" || clean == "paint" || clean == "paint-shop" -> "paint_001"
                else -> clean.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            }
        }

        fun getBlockId(blockNameOrId: String?): String {
            return normalizeBlockId(blockNameOrId)
        }

        fun getBlockName(blockNameOrId: String?): String {
            val clean = (blockNameOrId ?: "").trim().lowercase(Locale.ROOT)
            return when {
                clean == "vblock_001" || clean == "v-block" || clean == "v block" || clean == "v_block" || clean == "v" -> "V-Block"
                clean == "qblock_001" || clean == "q-block" || clean == "q block" || clean == "q_block" || clean == "q" -> "Q-Block"
                clean == "sblock_001" || clean == "s-block" || clean == "s block" || clean == "s_block" || clean == "s" -> "S-Block"
                clean == "tblock_001" || clean == "t-block" || clean == "t block" || clean == "t_block" || clean == "t" -> "T-Block"
                clean == "e_block" || clean == "e-block" || clean == "e block" || clean == "e_block" || clean == "e" -> "E-Block"
                clean == "rblock_001" || clean == "r-block" || clean == "r block" || clean == "r_block" || clean == "r" -> "R-Block"
                clean == "pblock_001" || clean == "p-block" || clean == "p block" || clean == "p_block" || clean == "p" -> "P-Block"
                clean == "d1block_001" || clean == "d1-block" || clean == "d1 block" || clean == "d1_block" || clean == "d1" -> "D1-Block"
                clean == "hblock_001" || clean == "h-block" || clean == "h block" || clean == "h_block" || clean == "h" -> "H-Block"
                clean == "assembly_001" || clean == "assembly" || clean == "assembly-line-1" -> "Assembly-Line-1"
                clean == "paint_001" || clean == "paint" || clean == "paint-shop" -> "Paint-Shop"
                clean.isNotBlank() -> blockNameOrId!!.trim()
                else -> "V-Block"
            }
        }

        fun normalizeCompany(companyStr: String?): String {
            return getCompanyName(companyStr)
        }

        fun normalizeBlock(blockStr: String?): String {
            return getBlockName(blockStr)
        }

        fun isMatchingCompany(itemCompany: String?, targetCompany: String?): Boolean {
            val idTarget = getCompanyId(targetCompany)
            val idItem = getCompanyId(itemCompany)
            return idItem.equals(idTarget, ignoreCase = true)
        }

        fun isMatchingBlock(itemBlock: String?, targetBlock: String?): Boolean {
            val idTarget = getBlockId(targetBlock)
            val idItem = getBlockId(itemBlock)
            return idItem.equals(idTarget, ignoreCase = true)
        }
    }

    private fun getScopedPath(company: String, block: String, collectionName: String): DatabaseReference {
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)
        return db.child("Companies").child(compId).child("Blocks").child(blkId).child(collectionName)
    }

    fun initializeCompanyAndBlockStructure(onComplete: (Boolean) -> Unit = {}) {
        val timestamp = System.currentTimeMillis()

        val companyStructure = mapOf(
            "mahindra_001" to mapOf(
                "companyId" to "mahindra_001",
                "name" to "Mahindra",
                "createdAt" to timestamp,
                "Blocks" to mapOf(
                    "vblock_001" to mapOf("blockId" to "vblock_001", "name" to "V-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "qblock_001" to mapOf("blockId" to "qblock_001", "name" to "Q-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "e_block" to mapOf("blockId" to "e_block", "name" to "E-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "sblock_001" to mapOf("blockId" to "sblock_001", "name" to "S-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "tblock_001" to mapOf("blockId" to "tblock_001", "name" to "T-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "rblock_001" to mapOf("blockId" to "rblock_001", "name" to "R-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "pblock_001" to mapOf("blockId" to "pblock_001", "name" to "P-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "d1block_001" to mapOf("blockId" to "d1block_001", "name" to "D1-Block", "companyId" to "mahindra_001", "createdAt" to timestamp),
                    "hblock_001" to mapOf("blockId" to "hblock_001", "name" to "H-Block", "companyId" to "mahindra_001", "createdAt" to timestamp)
                )
            ),
            "skoda_001" to mapOf(
                "companyId" to "skoda_001",
                "name" to "Skoda",
                "createdAt" to timestamp,
                "Blocks" to mapOf(
                    "assembly_001" to mapOf("blockId" to "assembly_001", "name" to "Assembly-Line-1", "companyId" to "skoda_001", "createdAt" to timestamp),
                    "paint_001" to mapOf("blockId" to "paint_001", "name" to "Paint-Shop", "companyId" to "skoda_001", "createdAt" to timestamp)
                )
            ),
            "jsw_001" to mapOf(
                "companyId" to "jsw_001",
                "name" to "JSW",
                "createdAt" to timestamp,
                "Blocks" to mapOf(
                    "assembly_001" to mapOf("blockId" to "assembly_001", "name" to "Assembly-Line-1", "companyId" to "jsw_001", "createdAt" to timestamp)
                )
            )
        )

        db.child("Companies").get().addOnSuccessListener { snapshot ->
            try {
                for ((compId, compData) in companyStructure) {
                    if (!snapshot.hasChild(compId)) {
                        performLoggedWrite(db.child("Companies").child(compId), compData, "Initialize Company $compId")
                    } else {
                        val compRef = db.child("Companies").child(compId)
                        val compName = (compData["name"] as? String) ?: compId
                        compRef.child("companyId").setValue(compId)
                        compRef.child("name").setValue(compName)

                        val blocksMap = compData["Blocks"] as? Map<String, Map<String, Any>> ?: emptyMap()
                        for ((blkId, blkData) in blocksMap) {
                            val blkRef = compRef.child("Blocks").child(blkId)
                            val blkName = (blkData["name"] as? String) ?: blkId
                            blkRef.child("blockId").setValue(blkId)
                            blkRef.child("name").setValue(blkName)
                            blkRef.child("companyId").setValue(compId)
                        }
                    }
                }
                onComplete(true)
            } catch (e: Exception) {
                Log.e("FirebaseManager", "initialize error", e)
                onComplete(false)
            }
        }.addOnFailureListener { e ->
            Log.e("FirebaseManager", "initializeCompanyAndBlockStructure error", e)
            onComplete(false)
        }
    }

    // --- Dynamic Block List for Company ---

    fun getBlocksForCompany(company: String = ""): Flow<List<BlockInfo>> = callbackFlow {
        val compId = getCompanyId(company)
        if (compId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<BlockInfo>()
                    for (blockChild in snapshot.children) {
                        val blkId = blockChild.child("blockId").getValue(String::class.java) ?: blockChild.key ?: continue
                        val name = blockChild.child("name").getValue(String::class.java) ?: getBlockName(blkId)
                        val created = blockChild.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                        list.add(BlockInfo(blockId = blkId, name = name, companyId = compId, createdAt = created))
                    }
                    if (list.isEmpty()) {
                        list.add(BlockInfo(blockId = "vblock_001", name = "V-Block", companyId = compId))
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getBlocksForCompany error", e)
                    trySend(listOf(BlockInfo("vblock_001", "V-Block", compId)))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getBlocksForCompany cancelled: ${error.message}", error.toException())
                trySend(listOf(BlockInfo("vblock_001", "V-Block", compId)))
                close()
            }
        }

        db.child("Companies").child(compId).child("Blocks").addValueEventListener(listener)
        awaitClose { db.child("Companies").child(compId).child("Blocks").removeEventListener(listener) }
    }

    fun addBlockToCompany(company: String, blockName: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val cleanComp = company.trim()
        val cleanName = blockName.trim()
        if (cleanComp.isBlank() || cleanName.isBlank()) {
            onComplete(false, "Company and Block name cannot be blank")
            return
        }

        val compId = getCompanyId(cleanComp)
        val slug = cleanName.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
        val blkId = if (slug.endsWith("_001")) slug else "${slug}_001"

        val blkRef = db.child("Companies").child(compId).child("Blocks").child(blkId)
        val timestamp = System.currentTimeMillis()

        val blockData = mapOf<String, Any>(
            "blockId" to blkId,
            "name" to cleanName,
            "companyId" to compId,
            "createdAt" to timestamp
        )

        blkRef.setValue(blockData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onComplete(true, "Block '$cleanName' added successfully!")
            } else {
                onComplete(false, "Failed to add block: ${task.exception?.message}")
            }
        }
    }

    fun removeBlockFromCompany(companyName: String, blockName: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanComp = companyName.trim()
        val cleanBlock = normalizeBlock(blockName)
        if (cleanComp.isBlank() || cleanBlock.isBlank()) {
            onComplete(false)
            return
        }
        val compId = getCompanyId(cleanComp)
        val blkId = getBlockId(cleanBlock)
        db.child("Companies").child(compId).child("Blocks").child(blkId).removeValue().addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun saveCompany(companyName: String, blocks: List<String>, onComplete: (Boolean) -> Unit = {}) {
        val cleanCompName = companyName.trim()
        if (cleanCompName.isBlank()) {
            onComplete(false)
            return
        }
        val compId = getCompanyId(cleanCompName)
        val compRef = db.child("Companies").child(compId)

        compRef.child("companyId").setValue(compId)
        compRef.child("name").setValue(cleanCompName)

        val cleanBlocks = if (blocks.isNotEmpty()) {
            blocks.map { normalizeBlock(it) }.filter { it.isNotBlank() }
        } else {
            listOf("V-Block", "Assembly")
        }

        for (blkName in cleanBlocks) {
            val blkId = getBlockId(blkName)
            val blkRef = compRef.child("Blocks").child(blkId)
            blkRef.child("blockId").setValue(blkId)
            blkRef.child("name").setValue(blkName)
            blkRef.child("companyId").setValue(compId)
        }
        onComplete(true)
    }

    fun deleteCompany(companyName: String) {
        val clean = companyName.trim()
        if (clean.isNotBlank()) {
            val compId = getCompanyId(clean)
            db.child("Companies").child(compId).removeValue()
        }
    }

    // --- Original Tools ---

    fun getOriginalTools(company: String = "", block: String = ""): Flow<List<OriginalTool>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("original_tools")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<OriginalTool>()
                    for (child in snapshot.children) {
                        child.getValue(OriginalTool::class.java)?.let { tool ->
                            list.add(tool.copy(company = targetCompany, block = targetBlock))
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getOriginalTools error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getOriginalTools cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun saveOriginalTool(tool: OriginalTool) {
        val normComp = normalizeCompany(tool.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(tool.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val toolToSave = tool.copy(company = normComp, block = normBlk)
        val targetRef = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("original_tools").child(toolToSave.toolId)
        performLoggedWrite(targetRef, toolToSave, "Save Original Tool ${toolToSave.toolId}")
    }

    fun updateOriginalToolFields(
        toolId: String,
        company: String = "",
        block: String = "",
        fields: Map<String, Any>
    ) {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)

        if (toolId.isNotBlank() && fields.isNotEmpty()) {
            val updateMap = HashMap<String, Any>(fields)
            updateMap["lastUpdated"] = ServerValue.TIMESTAMP
            db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("original_tools").child(toolId).updateChildren(updateMap)
        }
    }

    fun deleteOriginalTool(toolId: String, company: String = "", block: String = "") {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)

        if (toolId.isNotBlank()) {
            performLoggedDelete(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("original_tools").child(toolId), "Delete Tool $toolId")
            performLoggedDelete(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("pm_entries").child("PM_$toolId"), "Delete PM Entry PM_$toolId")
        }
    }

    // --- Backup Tools ---

    fun getBackupTools(company: String = "", block: String = ""): Flow<List<BackupTool>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("backup_tools")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<BackupTool>()
                    for (child in snapshot.children) {
                        child.getValue(BackupTool::class.java)?.let { tool ->
                            list.add(tool.copy(company = targetCompany, block = targetBlock))
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getBackupTools error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getBackupTools cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun saveBackupTool(tool: BackupTool) {
        val normComp = normalizeCompany(tool.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(tool.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val toolToSave = tool.copy(company = normComp, block = normBlk)
        val targetRef = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("backup_tools").child(toolToSave.toolId)
        performLoggedWrite(targetRef, toolToSave, "Save Backup Tool ${toolToSave.toolId}")
    }

    fun deleteBackupTool(toolId: String, company: String = "", block: String = "") {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)
        if (toolId.isNotBlank()) {
            performLoggedDelete(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("backup_tools").child(toolId), "Delete Backup Tool $toolId")
        }
    }

    fun updateBackupToolStatus(toolId: String, company: String = "", block: String = "", status: String) {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)

        if (toolId.isNotBlank()) {
            val updates = mapOf<String, Any>(
                "status" to status.trim(),
                "lastUpdated" to ServerValue.TIMESTAMP
            )
            db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("backup_tools").child(toolId).updateChildren(updates)
        }
    }

    fun getMasterCompanyList(onResult: (Map<String, List<String>>) -> Unit) {
        db.child("Companies").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val map = mutableMapOf<String, List<String>>()
                    for (companyChild in snapshot.children) {
                        val companyName = companyChild.child("name").getValue(String::class.java)
                            ?: companyChild.key
                            ?: continue
                        val blocksNode = companyChild.child("Blocks")
                        val blocksList = mutableListOf<String>()
                        for (blockChild in blocksNode.children) {
                            val blockName = blockChild.child("name").getValue(String::class.java)
                                ?: blockChild.key
                                ?: continue
                            blocksList.add(blockName)
                        }
                        map[companyName] = blocksList
                    }
                    onResult(map)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getMasterCompanyList error", e)
                    onResult(emptyMap())
                }
            }

            override fun onCancelled(error: DatabaseError) { onResult(emptyMap()) }
        })
    }

    fun verifyBackupTool(
        tool: BackupTool,
        isOk: Boolean,
        verifiedBy: String,
        statusOverride: String = "",
        reason: String = "",
        remarks: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val normComp = normalizeCompany(tool.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(tool.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val now = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val nextAt = now + (24 * 60 * 60 * 1000L)

        val newStatus = when {
            statusOverride.isNotBlank() -> statusOverride
            isOk -> "Available"
            else -> "Verification Hold"
        }
        val verificationStatusStr = if (isOk) "OK" else "HOLD"

        val updates = mapOf<String, Any>(
            "isVerifiedToday" to isOk,
            "verifiedAt" to now,
            "verifiedBy" to verifiedBy,
            "verificationStatus" to verificationStatusStr,
            "lastVerifiedTimestamp" to now,
            "nextVerificationAt" to nextAt,
            "lastVerificationDate" to todayStr,
            "status" to newStatus,
            "remarks" to remarks.ifEmpty { tool.remarks },
            "updatedAt" to now
        )

        val scopedRef = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("backup_tools").child(tool.toolId)
        performLoggedUpdate(scopedRef, updates, "Verify Backup Tool ${tool.toolId}")

        onComplete(true)
    }

    // --- Original Tools Status Update ---

    fun updateOriginalToolStatus(toolId: String, company: String = "", block: String = "", status: String, onComplete: (Boolean) -> Unit = {}) {
        val normComp = normalizeCompany(company).ifBlank { "Mahindra" }
        val normBlk = normalizeBlock(block).ifBlank { "V-Block" }
        val normCompId = getCompanyId(normComp)
        val normBlkId = getBlockId(normBlk)

        if (toolId.isBlank() || normCompId.isBlank() || normBlkId.isBlank()) {
            onComplete(false)
            return
        }

        val updates = mapOf<String, Any>(
            "status" to status,
            "lastUpdated" to ServerValue.TIMESTAMP
        )
        val ref = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("original_tools").child(toolId)
        performLoggedUpdate(ref, updates, "Update Original Tool $toolId status to $status")
        onComplete(true)
    }

    // --- Breakdown Entries ---

    fun getBreakdownEntries(company: String = "", block: String = ""): Flow<List<BreakdownEntry>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("breakdown_entries")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<BreakdownEntry>()
                    for (child in snapshot.children) {
                        child.getValue(BreakdownEntry::class.java)?.let { entry ->
                            list.add(entry.copy(company = targetCompany, block = targetBlock))
                        }
                    }
                    list.sortByDescending { it.createdAt }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getBreakdownEntries error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getBreakdownEntries cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun saveBreakdownEntry(entry: BreakdownEntry) {
        val normComp = normalizeCompany(entry.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(entry.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val key = if (entry.entryId.isNotEmpty()) entry.entryId else db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("breakdown_entries").push().key ?: UUID.randomUUID().toString()

        val updatedEntry = (if (entry.entryId.isEmpty()) entry.copy(entryId = key) else entry).copy(
            company = normComp,
            block = normBlk
        )

        val toolId = updatedEntry.originalToolId.trim()
        val currentStatusUpper = updatedEntry.status.trim().uppercase(Locale.ROOT)
        val newToolStatus = when {
            currentStatusUpper == "OPEN" || currentStatusUpper == "REPORTED" || currentStatusUpper == "BREAKDOWN" -> "Breakdown"
            currentStatusUpper == "IN REPAIR" || currentStatusUpper == "TESTING" || currentStatusUpper.contains("REPAIR") -> "Under Repair"
            currentStatusUpper == "HANDOVER COMPLETED" || currentStatusUpper == "CLOSED" || currentStatusUpper == "OK" -> "OK"
            else -> "Breakdown"
        }

        val multiPathUpdates = HashMap<String, Any>()
        multiPathUpdates["Companies/$normCompId/Blocks/$normBlkId/breakdown_entries/$key"] = updatedEntry
        if (toolId.isNotBlank()) {
            multiPathUpdates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/status"] = newToolStatus
            multiPathUpdates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/lastUpdated"] = ServerValue.TIMESTAMP
        }

        performLoggedUpdate(db, multiPathUpdates, "Atomic Multi-Path Breakdown $key & Tool $toolId to $newToolStatus")
    }

    fun deleteBreakdownEntry(entryId: String, company: String = "", block: String = "") {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)
        if (entryId.isNotBlank()) {
            performLoggedDelete(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("breakdown_entries").child(entryId), "Delete Breakdown Entry $entryId")
        }
    }

    // --- PM Entries & History ---

    fun getPmEntries(company: String = "", block: String = ""): Flow<List<PmEntry>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        fun isFilterableActivePm(entry: PmEntry): Boolean {
            if (entry.completed) return false
            val statusClean = entry.status.trim().lowercase(Locale.ROOT)
            if (statusClean == "completed" || statusClean == "done" || statusClean == "pass") return false
            if (entry.pmId.contains("_HIST_") || entry.pmId.contains("_LOG_")) return false
            return true
        }

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("pm_entries")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<PmEntry>()
                    for (child in snapshot.children) {
                        child.getValue(PmEntry::class.java)?.let { entry ->
                            if (isFilterableActivePm(entry)) {
                                list.add(entry.copy(company = targetCompany, block = targetBlock))
                            }
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getPmEntries error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getPmEntries cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun savePmEntry(pmEntry: PmEntry) {
        val normComp = normalizeCompany(pmEntry.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(pmEntry.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val entryToSave = pmEntry.copy(company = normComp, block = normBlk)
        val key = if (entryToSave.pmId.isNotEmpty()) entryToSave.pmId else "PM_${entryToSave.toolId}"
        db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("pm_entries").child(key).setValue(entryToSave.copy(pmId = key))
    }

    fun deletePmEntry(pmId: String, company: String = "", block: String = "") {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)
        if (pmId.isNotBlank()) {
            performLoggedDelete(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("pm_entries").child(pmId), "Delete PM Entry $pmId")
        }
    }

    fun getPmHistory(company: String = "", block: String = "", toolId: String? = null): Flow<List<PmHistoryEntry>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("pm_history")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<PmHistoryEntry>()
                    for (child in snapshot.children) {
                        child.getValue(PmHistoryEntry::class.java)?.let { entry ->
                            if (toolId == null || entry.toolId.equals(toolId, ignoreCase = true)) {
                                list.add(entry.copy(company = targetCompany, block = targetBlock))
                            }
                        }
                    }
                    trySend(list.sortedByDescending { it.timestamp })
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getPmHistory error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getPmHistory cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun savePmHistoryEntry(historyEntry: PmHistoryEntry) {
        val normComp = normalizeCompany(historyEntry.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(historyEntry.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val entryToSave = historyEntry.copy(company = normComp, block = normBlk)
        val key = if (entryToSave.historyId.isNotEmpty()) entryToSave.historyId else db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("pm_history").push().key ?: UUID.randomUUID().toString()
        db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("pm_history").child(key).setValue(entryToSave.copy(historyId = key))
    }

    fun completePmTransaction(
        historyEntry: PmHistoryEntry,
        nextActiveSchedule: PmEntry,
        updatedTool: OriginalTool,
        sparePartsUsed: List<UsedSparePart> = emptyList(),
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val targetCompany = normalizeCompany(historyEntry.company).ifBlank { "Mahindra" }
        val targetCompId = getCompanyId(targetCompany)
        val targetBlock = normalizeBlock(historyEntry.block).ifBlank { "V-Block" }
        val targetBlkId = getBlockId(targetBlock)

        val toolId = historyEntry.toolId
        val currentPmKey = "PM_$toolId"

        val activeScheduleToSave = PmEntry(
            pmId = currentPmKey,
            toolId = toolId,
            toolName = historyEntry.toolName,
            line = historyEntry.line,
            station = historyEntry.station,
            scheduledDate = nextActiveSchedule.scheduledDate,
            status = "Pending",
            startedAt = "",
            startedBy = "",
            partsUsed = historyEntry.partsUsed,
            completedDate = "",
            pmCycleMonths = if (historyEntry.pmCycleMonths > 0) historyEntry.pmCycleMonths else 6,
            active = true,
            completed = false,
            company = targetCompany,
            block = targetBlock
        )

        db.child("Companies").child(targetCompId).child("Blocks").child(targetBlkId).child("pm_entries").child(currentPmKey).setValue(activeScheduleToSave).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val histKey = if (historyEntry.historyId.isNotEmpty()) historyEntry.historyId else "PM_HIST_${toolId}_${System.currentTimeMillis()}"
                val finalHist = historyEntry.copy(
                    historyId = histKey,
                    status = "Completed",
                    company = targetCompany,
                    block = targetBlock
                )
                savePmHistoryEntry(finalHist)

                val finalTool = updatedTool.copy(
                    lastPmDate = historyEntry.actualCompletedDate,
                    nextPmDate = nextActiveSchedule.scheduledDate,
                    company = targetCompany,
                    block = targetBlock
                )
                if (finalTool.toolId.isNotEmpty()) {
                    saveOriginalTool(finalTool)
                }

                // Deduct stock safely with ServerValue.increment(-qty)
                for (part in sparePartsUsed) {
                    if (part.partId.isNotBlank() && part.quantityUsed > 0) {
                        val prodRef = db.child("Companies").child(targetCompId).child("Blocks").child(targetBlkId).child("inventory/products").child(part.partId)
                        prodRef.child("currentStock").setValue(ServerValue.increment(-part.quantityUsed.toLong()))

                        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                        val sale = SalesEntry(
                            timestamp = now,
                            productCode = part.partId,
                            productName = part.partName,
                            toolId = toolId,
                            qtySold = part.quantityUsed,
                            paymentMode = "Preventive Maintenance",
                            company = targetCompany,
                            block = targetBlock,
                            remarks = "Deducted during PM completion for $toolId"
                        )
                        recordSale(sale)
                    }
                }

                onSuccess()
            } else {
                onFailure(task.exception ?: Exception("Firebase write failed"))
            }
        }
    }

    fun logAdminDateChangeAudit(
        toolId: String,
        oldDate: String,
        newDate: String,
        changedBy: String,
        changedByName: String,
        reason: String
    ) {
        try {
            val key = db.child("pm_date_change_audit").push().key ?: UUID.randomUUID().toString()
            val auditMap = mapOf(
                "auditId" to key,
                "toolId" to toolId,
                "oldScheduledDate" to oldDate,
                "newScheduledDate" to newDate,
                "changedBy" to changedBy,
                "changedByName" to changedByName,
                "reason" to reason,
                "changedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            )
            db.child("pm_date_change_audit").child(key).setValue(auditMap)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "logAdminDateChangeAudit error", e)
        }
    }

    // --- Inventory & Products ---

    fun parseProductFromSnapshot(child: DataSnapshot): Product? {
        try {
            val direct = child.getValue(Product::class.java)

            val code = (child.child("productCode").getValue(String::class.java)
                ?: child.child("partNumber").getValue(String::class.java)
                ?: child.child("partCode").getValue(String::class.java)
                ?: child.child("partNo").getValue(String::class.java)
                ?: child.child("partId").getValue(String::class.java)
                ?: direct?.productCode
                ?: child.key ?: "").trim()

            val name = (child.child("productName").getValue(String::class.java)
                ?: child.child("partName").getValue(String::class.java)
                ?: direct?.productName ?: "").trim()

            val toolIdOrModel = (child.child("toolId").getValue(String::class.java)
                ?: child.child("toolModel").getValue(String::class.java)
                ?: child.child("compatibleToolModel").getValue(String::class.java)
                ?: direct?.toolId ?: "All Models").trim()

            val category = (child.child("category").getValue(String::class.java) ?: direct?.category ?: "General").ifBlank { "General" }
            val uom = (child.child("unitOfMeasure").getValue(String::class.java) ?: direct?.unitOfMeasure ?: "Nos").ifBlank { "Nos" }
            
            val price = child.child("unitPrice").getValue(Double::class.java)
                ?: child.child("price").getValue(Double::class.java)
                ?: direct?.unitPrice ?: 0.0

            val stock = child.child("currentStock").getValue(Int::class.java)
                ?: child.child("stock").getValue(Int::class.java)
                ?: child.child("qty").getValue(Int::class.java)
                ?: direct?.currentStock ?: 0

            val minStock = child.child("minStockLevel").getValue(Int::class.java)
                ?: child.child("minStock").getValue(Int::class.java)
                ?: direct?.minStockLevel ?: 5

            val opening = child.child("openingStock").getValue(Int::class.java) ?: direct?.openingStock ?: stock

            val company = normalizeCompany(child.child("company").getValue(String::class.java) ?: direct?.company)
            val block = normalizeBlock(child.child("block").getValue(String::class.java) ?: direct?.block)

            if (code.isNotBlank() || name.isNotBlank()) {
                return Product(
                    productCode = if (code.isNotBlank()) code else "PART-${System.currentTimeMillis()}",
                    productName = if (name.isNotBlank()) name else "Spare Part ($code)",
                    toolId = if (toolIdOrModel.isNotBlank()) toolIdOrModel else "All Models",
                    category = category,
                    unitOfMeasure = uom,
                    unitPrice = price,
                    openingStock = opening,
                    currentStock = stock,
                    minStockLevel = minStock,
                    company = company,
                    block = block
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "parseProductFromSnapshot error", e)
        }
        return null
    }

    fun getProducts(company: String = "", block: String = ""): Flow<List<Product>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val baseRef = db.child("Companies").child(compId).child("Blocks").child(blkId)
        val pathRef = baseRef.child("inventory/products")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val map = mutableMapOf<String, Product>()
                    for (child in snapshot.children) {
                        parseProductFromSnapshot(child)?.let { prod ->
                            map[prod.productCode] = prod.copy(company = targetCompany, block = targetBlock)
                        }
                    }

                    // Also check legacy "products" root node if present
                    baseRef.child("products").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(legacySnap: DataSnapshot) {
                            for (child in legacySnap.children) {
                                parseProductFromSnapshot(child)?.let { prod ->
                                    if (!map.containsKey(prod.productCode)) {
                                        map[prod.productCode] = prod.copy(company = targetCompany, block = targetBlock)
                                    }
                                }
                            }
                            trySend(map.values.toList())
                        }

                        override fun onCancelled(error: DatabaseError) {
                            trySend(map.values.toList())
                        }
                    })
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getProducts error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getProducts cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun saveProduct(product: Product) {
        val normComp = normalizeCompany(product.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(product.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val prodToSave = product.copy(company = normComp, block = normBlk)
        val targetRef = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/products").child(prodToSave.productCode)
        performLoggedWrite(targetRef, prodToSave, "Save Product ${prodToSave.productCode}")
    }

    fun deleteProduct(productCode: String, company: String = "", block: String = "") {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)
        if (productCode.isNotBlank()) {
            performLoggedDelete(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/products").child(productCode), "Delete Product $productCode")
        }
    }

    fun recordPurchase(purchase: PurchaseEntry) {
        val normComp = normalizeCompany(purchase.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(purchase.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val key = if (purchase.id.isNotEmpty()) purchase.id else db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/purchases").push().key ?: UUID.randomUUID().toString()
        val entryToSave = purchase.copy(id = key, company = normComp, block = normBlk)
        performLoggedWrite(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/purchases").child(key), entryToSave, "Record Purchase")
    }

    fun getPurchaseEntries(company: String = "", block: String = ""): Flow<List<PurchaseEntry>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("inventory/purchases")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<PurchaseEntry>()
                    for (child in snapshot.children) {
                        child.getValue(PurchaseEntry::class.java)?.let { entry ->
                            list.add(entry.copy(company = targetCompany, block = targetBlock))
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getPurchaseEntries error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getPurchaseEntries cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun recordSale(sale: SalesEntry) {
        val normComp = normalizeCompany(sale.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(sale.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val key = if (sale.id.isNotEmpty()) sale.id else db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/sales").push().key ?: UUID.randomUUID().toString()
        val entryToSave = sale.copy(id = key, company = normComp, block = normBlk)
        performLoggedWrite(db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/sales").child(key), entryToSave, "Record Sale")
    }

    fun getSalesEntries(company: String = "", block: String = ""): Flow<List<SalesEntry>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("inventory/sales")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<SalesEntry>()
                    for (child in snapshot.children) {
                        child.getValue(SalesEntry::class.java)?.let { entry ->
                            list.add(entry.copy(company = targetCompany, block = targetBlock))
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getSalesEntries error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getSalesEntries cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun deductProductStock(productCode: String, company: String = "", block: String = "", qtyToDeduct: Int, onComplete: (Boolean) -> Unit = {}) {
        val normCompId = getCompanyId(company)
        val normBlkId = getBlockId(block)

        if (productCode.isBlank() || qtyToDeduct <= 0) {
            onComplete(false)
            return
        }

        val prodRef = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/products").child(productCode)
        prodRef.child("currentStock").runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val current = currentData.getValue(Int::class.java) ?: 0
                if (current < qtyToDeduct) {
                    return com.google.firebase.database.Transaction.abort()
                }
                currentData.value = current - qtyToDeduct
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onComplete(error == null && committed)
            }
        })
    }

    // --- Technical Documents Library ---

    fun getToolTechnicalDocuments(company: String = "", block: String = "", toolId: String = ""): Flow<List<TechnicalDocument>> = callbackFlow {
        val targetCompany = normalizeCompany(company).ifBlank { "Mahindra" }
        val targetBlock = normalizeBlock(block).ifBlank { "V-Block" }

        val pathRef = getScopedPath(targetCompany, targetBlock, "technical_documents").child(toolId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<TechnicalDocument>()
                    for (child in snapshot.children) {
                        child.getValue(TechnicalDocument::class.java)?.let { doc ->
                            list.add(doc.copy(companyId = targetCompany, blockId = targetBlock))
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getToolTechnicalDocuments error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getToolTechnicalDocuments cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun saveTechnicalDocument(doc: TechnicalDocument) {
        val normComp = normalizeCompany(doc.companyId).ifBlank { "Mahindra" }
        val normBlk = normalizeBlock(doc.blockId).ifBlank { "V-Block" }
        val key = if (doc.id.isNotEmpty()) doc.id else getScopedPath(normComp, normBlk, "technical_documents").child(doc.toolId).push().key ?: UUID.randomUUID().toString()
        val docToSave = doc.copy(id = key, companyId = normComp, blockId = normBlk)

        val targetRef = getScopedPath(normComp, normBlk, "technical_documents").child(docToSave.toolId).child(key)
        performLoggedWrite(targetRef, docToSave, "Save Technical Document ${docToSave.id}")
    }

    fun deleteTechnicalDocument(company: String = "", block: String = "", toolId: String = "", docId: String = "") {
        val normComp = normalizeCompany(company).ifBlank { "Mahindra" }
        val normBlk = normalizeBlock(block).ifBlank { "V-Block" }
        if (toolId.isNotBlank() && docId.isNotBlank()) {
            performLoggedDelete(getScopedPath(normComp, normBlk, "technical_documents").child(toolId).child(docId), "Delete Tech Doc $docId")
        }
    }

    // --- Admin Settings & Password ---

    fun getAdminSettings(onResult: (String?, String?) -> Unit) {
        db.child("admin_settings").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val password = snapshot.child("password").getValue(String::class.java)
                    val pin = snapshot.child("recovery_pin").getValue(String::class.java)
                    onResult(password, pin)
                } catch (e: Exception) {
                    onResult(null, null)
                }
            }

            override fun onCancelled(error: DatabaseError) { onResult(null, null) }
        })
    }

    fun updateAdminPassword(newPass: String) {
        if (newPass.isNotBlank()) {
            db.child("admin_settings").child("password").setValue(newPass)
        }
    }

    // --- Notifications ---

    fun getNotifications(company: String = "", block: String = ""): Flow<List<NotificationModel>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<NotificationModel>()
                    for (child in snapshot.children) {
                        child.getValue(NotificationModel::class.java)?.let { notif ->
                            if (isMatchingCompany(notif.company, targetCompany) && isMatchingBlock(notif.block, targetBlock)) {
                                list.add(notif.copy(company = targetCompany, block = targetBlock))
                            }
                        }
                    }
                    list.sortByDescending { it.createdAt }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getNotifications error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getNotifications cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        db.child("notifications").addValueEventListener(listener)
        awaitClose { db.child("notifications").removeEventListener(listener) }
    }

    fun saveNotification(notification: NotificationModel) {
        val normComp = normalizeCompany(notification.company).ifBlank { "Mahindra" }
        val normBlk = normalizeBlock(notification.block).ifBlank { "V-Block" }
        val notifToSave = notification.copy(company = normComp, block = normBlk)
        val key = if (notifToSave.notificationId.isNotEmpty()) notifToSave.notificationId else db.child("notifications").push().key ?: UUID.randomUUID().toString()
        db.child("notifications").child(key).setValue(notifToSave.copy(notificationId = key))
    }

    fun markNotificationAsRead(notificationId: String) {
        if (notificationId.isBlank()) return
        val updates = mapOf<String, Any>(
            "isRead" to true,
            "readAt" to System.currentTimeMillis()
        )
        db.child("notifications").child(notificationId).updateChildren(updates)
    }

    fun markAllNotificationsAsRead(company: String = "", block: String = "") {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)

        db.child("notifications").get().addOnSuccessListener { snapshot ->
            try {
                for (child in snapshot.children) {
                    val notif = child.getValue(NotificationModel::class.java)
                    if (notif != null && !notif.isRead) {
                        if (isMatchingCompany(notif.company, targetCompany) && isMatchingBlock(notif.block, targetBlock)) {
                            child.ref.updateChildren(mapOf("isRead" to true, "readAt" to System.currentTimeMillis()))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "markAllNotificationsAsRead error", e)
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        if (notificationId.isNotBlank()) {
            db.child("notifications").child(notificationId).removeValue()
        }
    }

    // --- Calibration Entries ---

    fun getCalibrationEntries(company: String = "", block: String = ""): Flow<List<CalibrationEntry>> = callbackFlow {
        val targetCompany = normalizeCompany(company)
        val targetBlock = normalizeBlock(block)
        val compId = getCompanyId(company)
        val blkId = getBlockId(block)

        if (compId.isBlank() || blkId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val pathRef = db.child("Companies").child(compId).child("Blocks").child(blkId).child("calibration_entries")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<CalibrationEntry>()
                    for (child in snapshot.children) {
                        child.getValue(CalibrationEntry::class.java)?.let { entry ->
                            list.add(entry.copy(company = targetCompany, block = targetBlock))
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getCalibrationEntries error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getCalibrationEntries cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    fun saveCalibrationEntry(entry: CalibrationEntry) {
        val normComp = normalizeCompany(entry.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(entry.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val key = if (entry.id.isNotEmpty()) entry.id else db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("calibration_entries").push().key ?: UUID.randomUUID().toString()
        val entryToSave = (if (entry.id.isEmpty()) entry.copy(id = key) else entry).copy(
            company = normComp,
            block = normBlk
        )
        db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("calibration_entries").child(key).setValue(entryToSave)
    }

    // --- Users & Profiles ---

    fun parseUserProfileFromSnapshot(child: DataSnapshot): UserProfile? {
        try {
            val direct = child.getValue(UserProfile::class.java)

            val uid = (child.child("uid").getValue(String::class.java)
                ?: child.child("id").getValue(String::class.java)
                ?: direct?.uid
                ?: child.key ?: "").trim()

            val name = (child.child("name").getValue(String::class.java)
                ?: child.child("fullName").getValue(String::class.java)
                ?: child.child("displayName").getValue(String::class.java)
                ?: direct?.name ?: "").trim()

            val email = (child.child("email").getValue(String::class.java)
                ?: direct?.email ?: "").trim()

            val role = (child.child("role").getValue(String::class.java)
                ?: direct?.role ?: "technician").trim()

            val rawCompany = child.child("company").getValue(String::class.java) ?: direct?.company ?: ""
            val rawBlock = child.child("block").getValue(String::class.java) ?: direct?.block ?: ""
            val company = normalizeCompany(rawCompany)
            val block = normalizeBlockId(rawBlock)

            val approvalStatus = (child.child("approvalStatus").getValue(String::class.java)
                ?: child.child("accountStatus").getValue(String::class.java)
                ?: child.child("status").getValue(String::class.java)
                ?: direct?.approvalStatus ?: "PENDING").trim()

            val employeeId = child.child("employeeId").getValue(String::class.java) ?: direct?.employeeId ?: ""
            val department = child.child("department").getValue(String::class.java) ?: direct?.department ?: ""
            val designation = child.child("designation").getValue(String::class.java) ?: direct?.designation ?: ""
            val mobile = child.child("mobile").getValue(String::class.java) ?: direct?.mobile ?: ""
            val profilePhotoUrl = child.child("profilePhotoUrl").getValue(String::class.java) ?: direct?.profilePhotoUrl ?: ""
            val fcmToken = child.child("fcmToken").getValue(String::class.java) ?: direct?.fcmToken ?: ""

            val finalUid = if (uid.isNotBlank()) uid else child.key ?: ""

            if (finalUid.isNotBlank() || email.isNotBlank() || name.isNotBlank()) {
                return UserProfile(
                    uid = finalUid,
                    name = if (name.isNotBlank()) name else email.substringBefore("@"),
                    email = email,
                    role = role,
                    company = company,
                    block = block,
                    employeeId = employeeId,
                    department = department,
                    designation = designation,
                    mobile = mobile,
                    profilePhotoUrl = profilePhotoUrl,
                    fcmToken = fcmToken,
                    approvalStatus = approvalStatus
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "parseUserProfileFromSnapshot error", e)
        }
        return null
    }

    fun getUserProfileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val profile = parseUserProfileFromSnapshot(snapshot)
                    trySend(profile)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getUserProfileFlow error", e)
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getUserProfileFlow cancelled: ${error.message}", error.toException())
                trySend(null)
                close()
            }
        }

        db.child("users").child(uid).addValueEventListener(listener)
        awaitClose { db.child("users").child(uid).removeEventListener(listener) }
    }

    fun saveUserProfile(profile: UserProfile, onComplete: (Boolean) -> Unit = {}) {
        val normComp = normalizeCompany(profile.company)
        val normBlk = normalizeBlockId(profile.block)
        val profileToSave = profile.copy(company = normComp, block = normBlk)

        if (profileToSave.uid.isNotBlank()) {
            val userMap = mapOf(
                "uid" to profileToSave.uid,
                "name" to profileToSave.name,
                "fullName" to profileToSave.name,
                "displayName" to profileToSave.name,
                "email" to profileToSave.email,
                "role" to profileToSave.role,
                "company" to profileToSave.company,
                "block" to profileToSave.block,
                "employeeId" to profileToSave.employeeId,
                "department" to profileToSave.department,
                "designation" to profileToSave.designation,
                "mobile" to profileToSave.mobile,
                "profilePhotoUrl" to profileToSave.profilePhotoUrl,
                "fcmToken" to profileToSave.fcmToken,
                "approvalStatus" to profileToSave.approvalStatus,
                "accountStatus" to profileToSave.approvalStatus
            )
            db.child("users").child(profileToSave.uid).setValue(userMap).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        } else {
            onComplete(false)
        }
    }

    fun updateFcmToken(uid: String, token: String) {
        if (uid.isNotBlank() && token.isNotBlank()) {
            db.child("users").child(uid).child("fcmToken").setValue(token)
        }
    }

    fun getUserProfile(uid: String, onResult: (UserProfile?) -> Unit) {
        if (uid.isBlank()) {
            onResult(null)
            return
        }
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val profile = parseUserProfileFromSnapshot(snapshot)
                    onResult(profile)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getUserProfile error", e)
                    onResult(null)
                }
            }

            override fun onCancelled(error: DatabaseError) { onResult(null) }
        })
    }

    fun getUserProfileDetailed(uid: String, onResult: (UserProfile?, String?) -> Unit) {
        if (uid.isBlank()) {
            onResult(null, "Invalid User ID.")
            return
        }
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val profile = parseUserProfileFromSnapshot(snapshot)
                    if (profile != null) {
                        onResult(profile, null)
                    } else {
                        onResult(null, "प्रोफाईल मिळाला नाही, पुन्हा प्रयत्न करा.")
                    }
                } catch (e: Exception) {
                    onResult(null, e.message)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(null, error.message)
            }
        })
    }

    fun getUsers(company: String = "", block: String = ""): Flow<List<UserProfile>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<UserProfile>()
                    for (child in snapshot.children) {
                        parseUserProfileFromSnapshot(child)?.let { user ->
                            list.add(user)
                        }
                    }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getUsers error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getUsers cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        db.child("users").addValueEventListener(listener)
        awaitClose { db.child("users").removeEventListener(listener) }
    }

    fun getAllUsers(company: String = "", block: String = ""): Flow<List<UserProfile>> {
        return getUsers(company, block)
    }

    fun approveUser(uid: String, onComplete: (Boolean) -> Unit = {}) {
        if (uid.isNotBlank()) {
            val updates = mapOf<String, Any>(
                "approvalStatus" to "APPROVED",
                "accountStatus" to "APPROVED"
            )
            db.child("users").child(uid).updateChildren(updates).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }
    }

    fun rejectUser(uid: String, onComplete: (Boolean) -> Unit = {}) {
        if (uid.isNotBlank()) {
            val updates = mapOf<String, Any>(
                "approvalStatus" to "REJECTED",
                "accountStatus" to "REJECTED"
            )
            db.child("users").child(uid).updateChildren(updates).addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }
    }

    fun deleteUser(uid: String, onComplete: (Boolean) -> Unit = {}) {
        if (uid.isNotBlank()) {
            db.child("users").child(uid).removeValue().addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }
    }

    // --- Tool Allocation (Issue / Return) ---

    fun issueTool(
        tool: OriginalTool,
        line: String,
        station: String,
        person: String,
        currentUser: String = "Technician",
        onResult: (Boolean, String) -> Unit
    ) {
        val normComp = normalizeCompany(tool.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(tool.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val toolId = tool.toolId
        if (toolId.isBlank()) {
            onResult(false, "Invalid Tool ID.")
            return
        }

        val targetLine = line.ifBlank { "Line-1" }
        val targetStation = station.ifBlank { "ST-01" }

        val pushKey = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("tool_allocations").push().key ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))

        val allocationRecord = mapOf<String, Any>(
            "allocationId" to pushKey,
            "toolId" to toolId,
            "toolName" to tool.toolName,
            "action" to "Issue",
            "line" to targetLine,
            "station" to targetStation,
            "issuedToPerson" to person,
            "allocatedBy" to currentUser,
            "timestamp" to timestampStr,
            "timestampMs" to now,
            "company" to normComp,
            "block" to normBlk
        )

        val updates = HashMap<String, Any>()
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/line"] = targetLine
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/station"] = targetStation
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/assignedUser"] = person
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/lastUpdated"] = ServerValue.TIMESTAMP
        updates["Companies/$normCompId/Blocks/$normBlkId/tool_allocations/$pushKey"] = allocationRecord

        performLoggedUpdate(db, updates, "Issue Tool $toolId")
        onResult(true, "Tool $toolId issued to $targetLine - $targetStation ($person)")
    }

    fun returnTool(
        tool: OriginalTool,
        currentUser: String = "Technician",
        onResult: (Boolean, String) -> Unit
    ) {
        val normComp = normalizeCompany(tool.company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(tool.block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val toolId = tool.toolId
        if (toolId.isBlank()) {
            onResult(false, "Invalid Tool ID.")
            return
        }

        val pushKey = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("tool_allocations").push().key ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))

        val allocationRecord = mapOf<String, Any>(
            "allocationId" to pushKey,
            "toolId" to toolId,
            "toolName" to tool.toolName,
            "action" to "Return",
            "line" to "Standby Store",
            "station" to "Standby Store",
            "issuedToPerson" to "Standby Store",
            "allocatedBy" to currentUser,
            "timestamp" to timestampStr,
            "timestampMs" to now,
            "company" to normComp,
            "block" to normBlk
        )

        val updates = HashMap<String, Any>()
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/line"] = "Standby Store"
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/station"] = "Standby Store"
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/assignedUser"] = "Unassigned"
        updates["Companies/$normCompId/Blocks/$normBlkId/original_tools/$toolId/lastUpdated"] = ServerValue.TIMESTAMP
        updates["Companies/$normCompId/Blocks/$normBlkId/tool_allocations/$pushKey"] = allocationRecord

        performLoggedUpdate(db, updates, "Return Tool $toolId")
        onResult(true, "Tool $toolId returned to Standby Store")
    }

    // --- Audit Log Helpers ---

    fun logAuditAction(
        category: String,
        action: String,
        targetId: String = "",
        oldValue: String = "",
        newValue: String = "",
        performedBy: String = "Technician"
    ) {
        try {
            val key = db.child("audit_logs").push().key ?: UUID.randomUUID().toString()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val log = AuditLogEntry(
                id = key,
                timestamp = now,
                user = performedBy,
                module = category,
                action = action,
                recordId = targetId,
                oldValue = oldValue,
                newValue = newValue
            )
            db.child("audit_logs").child(key).setValue(log)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "logAuditAction error", e)
        }
    }

    fun getAuditLogs(): Flow<List<AuditLogEntry>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<AuditLogEntry>()
                    for (child in snapshot.children) {
                        child.getValue(AuditLogEntry::class.java)?.let { log ->
                            list.add(log)
                        }
                    }
                    list.sortByDescending { it.timestamp }
                    trySend(list)
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getAuditLogs error", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseManager", "getAuditLogs cancelled: ${error.message}", error.toException())
                trySend(emptyList())
                close()
            }
        }

        db.child("audit_logs").addValueEventListener(listener)
        awaitClose { db.child("audit_logs").removeEventListener(listener) }
    }

    // --- Super Admin, Trash & Multi-Path Atomic Operations ---

    fun checkIsSuperAdmin(uid: String): Flow<Boolean> = callbackFlow {
        if (uid.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val ref = db.child("Platform").child("superAdmins").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isSuper = snapshot.value == true || snapshot.getValue(Boolean::class.java) == true
                trySend(isSuper)
            }
            override fun onCancelled(error: DatabaseError) { trySend(false) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getTrashEntries(company: String = ""): Flow<List<TrashEntry>> = callbackFlow {
        val normComp = normalizeCompany(company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val ref = db.child("Trash").child(normCompId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<TrashEntry>()
                    for (child in snapshot.children) {
                        child.getValue(TrashEntry::class.java)?.let { list.add(it) }
                    }
                    trySend(list.sortedByDescending { it.deletedAt })
                } catch (e: Exception) {
                    Log.e("FirebaseManager", "getTrashEntries error", e)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun restoreTrashEntry(trashId: String, company: String = "", onComplete: (Boolean) -> Unit) {
        val normComp = normalizeCompany(company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val trashRef = db.child("Trash").child(normCompId).child(trashId)

        trashRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entry = snapshot.getValue(TrashEntry::class.java)
                if (entry != null && entry.originalPath.isNotBlank()) {
                    val targetRef = db.child(entry.originalPath)
                    val dataObj = snapshot.child("dataMap").value ?: snapshot.child("dataJson").value
                    if (dataObj != null) {
                        targetRef.setValue(dataObj).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                trashRef.removeValue()
                                logAuditAction("Trash / Recycle Bin", "Restore Record", entry.recordId, "TRASH", entry.originalPath)
                                onComplete(true)
                            } else {
                                onComplete(false)
                            }
                        }
                    } else {
                        onComplete(false)
                    }
                } else {
                    onComplete(false)
                }
            }
            override fun onCancelled(error: DatabaseError) { onComplete(false) }
        })
    }

    fun moveToTrash(
        company: String,
        block: String,
        collectionName: String,
        recordId: String,
        title: String,
        entityType: String,
        dataMap: Any,
        deletedBy: String,
        onComplete: (Boolean) -> Unit
    ) {
        val normComp = normalizeCompany(company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val origPath = "Companies/$normCompId/Blocks/$normBlkId/$collectionName/$recordId"
        val trashRef = db.child("Trash").child(normCompId)
        val key = trashRef.push().key ?: UUID.randomUUID().toString()

        val now = System.currentTimeMillis()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))

        val trashMap = mapOf(
            "id" to key,
            "entityType" to entityType,
            "originalPath" to origPath,
            "recordId" to recordId,
            "title" to title,
            "companyId" to normComp,
            "blockId" to normBlk,
            "deletedBy" to deletedBy,
            "deletedAt" to now,
            "deletedAtStr" to nowStr,
            "dataMap" to dataMap
        )

        val updates = mapOf<String, Any?>(
            "Trash/$normCompId/$key" to trashMap,
            "$origPath" to null
        )

        db.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                logAuditAction("Trash / Recycle Bin", "Move to Trash", recordId, origPath, "TRASH_$key", deletedBy)
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun performStockTransaction(
        company: String,
        block: String,
        productCode: String,
        type: String, // "STOCK_IN" or "STOCK_OUT"
        quantity: Int,
        reason: String = "",
        toolId: String = "",
        performedBy: String = "",
        remarks: String = "",
        onComplete: (Boolean, String, Product?) -> Unit
    ) {
        val normComp = normalizeCompany(company).ifBlank { "Mahindra" }
        val normCompId = getCompanyId(normComp)
        val normBlk = normalizeBlock(block).ifBlank { "V-Block" }
        val normBlkId = getBlockId(normBlk)

        val prodRef = db.child("Companies").child(normCompId).child("Blocks").child(normBlkId).child("inventory/products").child(productCode)

        prodRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val prod = currentData.getValue(Product::class.java) ?: return Transaction.success(currentData)

                val currentStock = prod.currentStock
                val newStock = if (type == "STOCK_IN") {
                    currentStock + quantity
                } else {
                    if (currentStock < quantity) {
                        return Transaction.abort()
                    }
                    currentStock - quantity
                }

                val updatedProd = prod.copy(
                    currentStock = newStock,
                    company = normComp,
                    block = normBlk,
                    toolId = if (toolId.isNotBlank()) toolId else prod.toolId
                )

                currentData.value = updatedProd
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed && snapshot != null) {
                    val updatedProduct = snapshot.getValue(Product::class.java)
                    val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                    if (type == "STOCK_IN") {
                        val purchase = PurchaseEntry(
                            timestamp = timeStr,
                            productCode = productCode,
                            productName = updatedProduct?.productName ?: "",
                            supplierName = reason.ifEmpty { "Store Refill" },
                            qtyPurchased = quantity,
                            unitPrice = updatedProduct?.unitPrice ?: 0.0,
                            totalAmount = quantity * (updatedProduct?.unitPrice ?: 0.0),
                            invoiceNo = "Refill-$timeStr",
                            company = normComp,
                            block = normBlk
                        )
                        recordPurchase(purchase)
                    } else {
                        val sale = SalesEntry(
                            timestamp = timeStr,
                            productCode = productCode,
                            productName = updatedProduct?.productName ?: "",
                            toolId = toolId,
                            qtySold = quantity,
                            salePrice = updatedProduct?.unitPrice ?: 0.0,
                            totalRevenue = quantity * (updatedProduct?.unitPrice ?: 0.0),
                            paymentMode = reason.ifEmpty { "Stock Issue" },
                            remarks = remarks.ifEmpty { performedBy },
                            company = normComp,
                            block = normBlk
                        )
                        recordSale(sale)
                    }

                    logAuditAction(
                        category = "Inventory",
                        action = type,
                        targetId = productCode,
                        performedBy = performedBy,
                        oldValue = "Qty: $quantity, Target Tool: $toolId",
                        newValue = "New Stock: ${updatedProduct?.currentStock}"
                    )

                    onComplete(true, "Transaction Successful", updatedProduct)
                } else {
                    val msg = error?.message ?: "Transaction aborted (insufficient stock or concurrency lock)"
                    onComplete(false, msg, null)
                }
            }
        })
    }

    private fun performLoggedWrite(targetRef: DatabaseReference, value: Any, actionDescription: String) {
        try {
            val pathStr = targetRef.toString()
            Log.d("FirebaseManager", "EXEC WRITE [$actionDescription]: Path = $pathStr")
            targetRef.setValue(value)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FirebaseManager", "SUCCESS WRITE [$actionDescription]: Path = $pathStr")
                    } else {
                        Log.e("FirebaseManager", "FAILURE WRITE [$actionDescription]: Path = $pathStr", task.exception)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseManager", "EXCEPTION WRITE [$actionDescription]: Path = $pathStr", e)
                }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "performLoggedWrite error", e)
        }
    }

    private fun performLoggedUpdate(targetRef: DatabaseReference, updates: Map<String, Any>, actionDescription: String) {
        try {
            val pathStr = targetRef.toString()
            Log.d("FirebaseManager", "EXEC UPDATE [$actionDescription]: Path = $pathStr")
            targetRef.updateChildren(updates)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FirebaseManager", "SUCCESS UPDATE [$actionDescription]: Path = $pathStr")
                    } else {
                        Log.e("FirebaseManager", "FAILURE UPDATE [$actionDescription]: Path = $pathStr", task.exception)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseManager", "EXCEPTION UPDATE [$actionDescription]: Path = $pathStr", e)
                }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "performLoggedUpdate error", e)
        }
    }

    private fun performLoggedDelete(targetRef: DatabaseReference, actionDescription: String) {
        try {
            val pathStr = targetRef.toString()
            Log.d("FirebaseManager", "EXEC DELETE [$actionDescription]: Path = $pathStr")
            targetRef.removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FirebaseManager", "SUCCESS DELETE [$actionDescription]: Path = $pathStr")
                    } else {
                        Log.e("FirebaseManager", "FAILURE DELETE [$actionDescription]: Path = $pathStr", task.exception)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseManager", "EXCEPTION DELETE [$actionDescription]: Path = $pathStr", e)
                }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "performLoggedDelete error", e)
        }
    }
}
