package com.example.instatracker.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scroll_events",
    foreignKeys = [
        ForeignKey(
            entity = ReelEntity::class,
            parentColumns = ["reelId"],
            childColumns = ["reelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reelId")]
)
data class ScrollEventEntity(
    @PrimaryKey(autoGenerate = true)
    val eventId: Long = 0,

    val reelId: Long,
    val timestamp: Long,
    val velocity: Double,
    val acceleration: Double
)
