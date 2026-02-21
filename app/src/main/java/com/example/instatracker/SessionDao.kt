package com.example.instatracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY sessionStart DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY sessionStart DESC")
    suspend fun getAllSessionsSync(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun clearAll()
}
