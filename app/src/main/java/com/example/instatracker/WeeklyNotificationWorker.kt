package com.example.instatracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chaquo.python.Python
import java.io.File
import org.json.JSONObject

/**
 * WeeklyNotificationWorker
 * Runs once per week (Sunday 9 AM) to compute and display weekly doom statistics.
 * Calls Python compute_weekly_summary() to get insights about doom rate trends.
 */
class WeeklyNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "weekly_summary"
        private const val NOTIFICATION_ID = 88888  // Unique ID for weekly summary
    }

    override fun doWork(): Result {
        return try {
            Log.d("WeeklyWorker", "Computing weekly summary...")
            
            // Get model state file path
            val modelStatePath = File(applicationContext.filesDir, "alse_model_state.json").absolutePath
            
            // Initialize Python runtime
            if (!Python.isStarted()) {
                Python.start(com.chaquo.python.android.AndroidPlatform(applicationContext))
            }
            val py = Python.getInstance()
            
            // Call Python function to compute weekly summary
            val summaryJson = synchronized(InstaAccessibilityService.GLOBAL_PYTHON_LOCK) {
                val reelio = py.getModule("reelio_alse")
                val summaryObj = reelio.callAttr("compute_weekly_summary", modelStatePath)
                convertPythonDictToJson(summaryObj)
            }
            Log.d("WeeklyWorker", "Summary: $summaryJson")
            
            // Parse and extract key fields
            val thisWeekRate = summaryJson.optDouble("this_week_doom_rate", 0.0)
            val lastWeekRate = summaryJson.optDouble("last_week_doom_rate", 0.0)
            val deltaPercentage = summaryJson.optDouble("delta_pct", 0.0)
            val insight = summaryJson.optString("insight", "Check your weekly doom statistics.")
            val sessionCountThis = summaryJson.optInt("session_count_this_week", 0)
            
            Log.d("WeeklyWorker", 
                "Weekly Doom Rate: $thisWeekRate (vs $lastWeekRate last week, ${String.format("%.0f", deltaPercentage)}% change)")
            
            // Create and display notification
            createNotificationChannel(applicationContext)
            showWeeklyNotification(insight, thisWeekRate, deltaPercentage, sessionCountThis)
            
            Result.success()
        } catch (e: Exception) {
            Log.e("WeeklyWorker", "Error computing weekly summary: ${e.message}", e)
            Result.retry()  // Retry on failure
        }
    }

    private fun convertPythonDictToJson(pythonDict: com.chaquo.python.PyObject?): JSONObject {
        if (pythonDict == null) return JSONObject()
        return try {
            val result = JSONObject()
            val map = pythonDict.asMap()
            for ((k, v) in map) {
                val key = k.toString()
                when {
                    v == null                         -> result.put(key, JSONObject.NULL)
                    v.toString() == "True"            -> result.put(key, true)
                    v.toString() == "False"           -> result.put(key, false)
                    else -> {
                        val s = v.toString()
                        result.put(key, s.toDoubleOrNull() ?: s.toIntOrNull() ?: s)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.w("WeeklyWorker", "PyObject→JSON failed: ${e.message}")
            JSONObject()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Weekly Reelio Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your weekly doomscrolling insights"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showWeeklyNotification(
        insight: String,
        doomRate: Double,
        @Suppress("UNUSED_PARAMETER") deltaPct: Double,
        @Suppress("UNUSED_PARAMETER") sessionCount: Int
    ) {
        val contentText = insight
        
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aware_notification)
            .setContentTitle("📊 Your Weekly Reelio Insight")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d("WeeklyWorker", "Weekly notification shown: $contentText")
    }
}
