package com.example.toolcare

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineSyncManager private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.breakdownDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount

    private val _isOnline = MutableStateFlow(isNetworkConnected(context))
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        registerNetworkCallback()
        refreshPendingCount()
    }

    companion object {
        @Volatile
        private var INSTANCE: OfflineSyncManager? = null

        fun getInstance(context: Context): OfflineSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun isNetworkConnected(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (e: Exception) {
                false
            }
        }
    }

    fun refreshPendingCount() {
        scope.launch {
            try {
                val unsynced = dao.getUnsyncedBreakdowns()
                _pendingSyncCount.value = unsynced.size
            } catch (e: Exception) {
                Log.e("OfflineSyncManager", "Error counting pending sync items", e)
            }
        }
    }

    fun saveBreakdownOffline(
        entry: BreakdownEntry,
        firebaseManager: FirebaseManager,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            try {
                val entity = LocalBreakdownEntity(
                    entryId = entry.entryId,
                    originalToolId = entry.originalToolId,
                    backupToolId = entry.backupToolId,
                    line = entry.line,
                    station = entry.station,
                    problem = entry.problem,
                    timestamp = entry.timestamp,
                    status = entry.status,
                    repairDetails = entry.repairDetails,
                    handoverPerson = entry.handoverPerson,
                    shift = entry.shift,
                    company = entry.company,
                    block = entry.block,
                    createdAt = entry.createdAt,
                    reportedBy = entry.reportedBy,
                    repairedAt = entry.repairedAt,
                    handoverAt = entry.handoverAt,
                    syncStatus = false
                )
                dao.insertBreakdown(entity)
                refreshPendingCount()

                if (isNetworkConnected(context)) {
                    firebaseManager.saveBreakdownEntry(entry)
                    dao.markAsSynced(entry.entryId)
                    refreshPendingCount()
                    withContext(Dispatchers.Main) {
                        onComplete(true, "Breakdown report submitted and synced online!")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(true, "⚡ Saved locally offline. Will automatically sync when online.")
                    }
                }
            } catch (e: Exception) {
                Log.e("OfflineSyncManager", "Error saving breakdown offline", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, "Error saving breakdown: ${e.message}")
                }
            }
        }
    }

    fun triggerSyncNow(firebaseManager: FirebaseManager) {
        scope.launch {
            try {
                if (!isNetworkConnected(context)) return@launch
                val unsynced = dao.getUnsyncedBreakdowns()
                for (entity in unsynced) {
                    val entry = BreakdownEntry(
                        entryId = entity.entryId,
                        originalToolId = entity.originalToolId,
                        backupToolId = entity.backupToolId,
                        line = entity.line,
                        station = entity.station,
                        problem = entity.problem,
                        timestamp = entity.timestamp,
                        status = entity.status,
                        repairDetails = entity.repairDetails,
                        handoverPerson = entity.handoverPerson,
                        shift = entity.shift,
                        company = entity.company,
                        block = entity.block,
                        createdAt = entity.createdAt,
                        reportedBy = entity.reportedBy,
                        repairedAt = entity.repairedAt,
                        handoverAt = entity.handoverAt
                    )
                    firebaseManager.saveBreakdownEntry(entry)
                    dao.markAsSynced(entity.entryId)
                }
                refreshPendingCount()
            } catch (e: Exception) {
                Log.e("OfflineSyncManager", "Error during triggerSyncNow", e)
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    Log.d("OfflineSyncManager", "Network connected! Triggering background auto-sync...")
                    val firebaseManager = FirebaseManager.getInstance()
                    triggerSyncNow(firebaseManager)
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                    Log.d("OfflineSyncManager", "Network connection lost.")
                }
            })
        } catch (e: Exception) {
            Log.e("OfflineSyncManager", "Failed to register network callback", e)
        }
    }
}
