package com.example.toolcare

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownDao {
    @Query("SELECT * FROM local_breakdowns ORDER BY createdAt DESC")
    fun getAllLocalBreakdowns(): Flow<List<LocalBreakdownEntity>>

    @Query("SELECT * FROM local_breakdowns WHERE syncStatus = 0")
    suspend fun getUnsyncedBreakdowns(): List<LocalBreakdownEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakdown(entity: LocalBreakdownEntity)

    @Query("UPDATE local_breakdowns SET syncStatus = 1 WHERE entryId = :id")
    suspend fun markAsSynced(id: String)
}
