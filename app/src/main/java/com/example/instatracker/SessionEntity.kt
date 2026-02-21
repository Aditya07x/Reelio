package com.example.instatracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val sessionStart: Long,
    val sessionEnd: Long?,
    val durationSeconds: Long,
    val timeOfDayCategory: String,
    val isLateNight: Boolean,
    val totalScrolls: Int,
    val maxReelStreak: Int,
    val burstCount: Int,
    val scrollsPerMinute: Float,
    val likeCount: Int,
    val commentClickCount: Int,
    val shareCount: Int,
    val immersionScore: Float,
    val totalReelsViewed: Int,
    val avgReelExposure: Float,
    val maxReelExposure: Float,
    val meanScrollInterval: Float,
    val scrollIntervalVariance: Float,
    val peakAcceleration: Float,
    val velocityProxy: Float,
    val maxVelocityProxy: Float,
    val avgBurstDuration: Float,
    val maxBurstDuration: Float
)

