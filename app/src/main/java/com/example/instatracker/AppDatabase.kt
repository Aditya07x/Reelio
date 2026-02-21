package com.example.instatracker

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN totalReelsViewed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN avgReelExposure REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN maxReelExposure REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN meanScrollInterval REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN scrollIntervalVariance REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN peakAcceleration REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN velocityProxy REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN maxVelocityProxy REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN avgBurstDuration REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN maxBurstDuration REAL NOT NULL DEFAULT 0")
            }
        }
    }
}

