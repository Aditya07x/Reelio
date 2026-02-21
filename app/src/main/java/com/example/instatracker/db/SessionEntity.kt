package com.example.instatracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,

    val startTime: Long,
    val endTime: Long? = null,

    val totalReels: Int = 0,
    val totalScrolls: Int = 0,
    val totalLikes: Int = 0,
    val totalComments: Int = 0,
    val totalPauses: Int = 0,

    val avgDwell: Double = 0.0,
    val dwellStd: Double = 0.0,
    val peakScrollVelocity: Double = 0.0
)
