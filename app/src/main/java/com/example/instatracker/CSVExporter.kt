package com.example.instatracker

import android.content.Context
import com.example.instatracker.db.AppDatabase
import java.io.File

class CSVExporter(private val context: Context, private val db: AppDatabase) {

    fun exportReels() {
        val file = File(
            context.getExternalFilesDir(null),
            "reels.csv"
        )

        val reels = db.reelDao().getAll()

        file.printWriter().use { writer ->
            writer.println(
                "SessionID,ReelIndex,StartTime,EndTime,Dwell,AvgVelocity,MaxVelocity,Friction,Liked,Commented,Paused,Immersion"
            )

            reels.forEach {
                writer.println(
                    "${it.sessionId},${it.reelIndex},${it.startTime},${it.endTime},${it.dwellTimeSec}," +
                            "${it.avgScrollSpeed},${it.maxScrollSpeed},${it.scrollFrictionIndex}," +
                            "${it.liked},${it.commented},${it.paused},${it.immersionScore}"
                )
            }
        }
    }
}
