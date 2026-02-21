package com.example.instatracker.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        ReelEntity::class,
        ScrollEventEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun reelDao(): ReelDao
    abstract fun scrollDao(): ScrollDao
}
