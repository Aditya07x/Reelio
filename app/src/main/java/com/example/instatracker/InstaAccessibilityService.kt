
package com.example.instatracker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class InstaAccessibilityService : AccessibilityService() {

    // Session State
    private var currentSessionNumber = 0
    private var sessionStartTime: Long? = null
    private val sessionTimeout = 30_000L
    private var lastActiveTime = 0L // Tracks user activity for session timeout

    // Reel Metrics
    private var reelCount = 0
    private var cumulativeReels = 0
    private var lastReelStartTime = 0L
    private var lastReelDebounceTime = 0L // Tracks last REEL CHANGE for debounce logic
    
    // Doomscroll Metrics
    private var continuousScrollCount = 0 // Reels scrolled without "pause" (dwell > 5s)
    
    // Scroll Speed
    private var scrollDistances = mutableListOf<Float>()
    private var lastScrollTime = 0L
    
    // Interactions for CURRENT reel
    private var liked = false
    private var commented = false
    private var shared = false // Share/Send
    private var saved = false // Save/Bookmark

    // Rolling stats
    private val dwellWindow = ArrayDeque<Double>()
    
    companion object {
        private const val WINDOW_SIZE = 5
        private const val PREFS_NAME = "InstaTrackerPrefs"
        private const val KEY_SESSION_NUM = "session_number"
    }

    // Formatting
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val CHANNEL_ID = "survey_channel"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("INSTA_DEBUG", "Service Connected! Ready to track.")
        ensureCsvHeader()
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        val packageName = event.packageName?.toString() ?: ""
        checkSessionTimeout(now, packageName)

        if (event.packageName != "com.instagram.android") {
            // User left Instagram?
            // If we have an active session, let's treat switching apps as a potential end signal
            // But usually we rely on timeout. Let's just rely on timeout OR manual "back" logic if needed.
            // For now, if they switch apps, we just let timeout handle it to avoid spamming 
            // if they just switch for 1 sec to reply to a message.
            return 
        }

        // 1. Session Start
        if (sessionStartTime == null) {
            startNewSession(now)
        }
        
        // 2. Interaction Detection (Clicks)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val desc = event.contentDescription?.toString()?.lowercase() ?: ""
            val text = event.text?.joinToString(" ")?.lowercase() ?: ""
            
            // Log for debugging labels
            // Log.d("INTERACTION", "Click: $desc | Text: $text")

            if (desc.contains("like") || text.contains("like")) liked = true
            if (desc.contains("reply") || desc.contains("comment") || text.contains("reply")) commented = true
            if (desc.contains("share") || desc.contains("send") || text.contains("share")) shared = true
            if (desc.contains("save") || desc.contains("bookmark") || text.contains("save")) saved = true
            
            resetSessionTimer(now) // Interaction keeps session alive
            return
        }

        // 3. Scroll Speed Tracking
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
             if (lastScrollTime != 0L) {
                 val timeDelta = now - lastScrollTime
                 if (timeDelta > 0 && timeDelta < 500) { // Valid scroll burst
                     val intensity = 1000f / timeDelta
                     scrollDistances.add(intensity)
                 }
             }
             lastScrollTime = now
             // Do NOT reset the debounce timer here. Only session activity.
             resetSessionTimer(now)
        }

        // 4. Reel Transition Detection
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
             event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
             
            // Using lastReelDebounceTime instead of lastEventTime to decouple scroll updates
            if (now - lastReelDebounceTime > 800) {
                // Determine if this was a "New Reel" transition
                if (lastReelStartTime != 0L) {
                    processPreviousReel(now)
                }
                
                // Start Next Reel
                startNextReel(now)
            }
        }
    }
    
    private fun startNextReel(now: Long) {
        reelCount++
        cumulativeReels++
        
        lastReelDebounceTime = now // Update debounce timer
        lastReelStartTime = now
        
        // Reset Interactions for NEW reel
        liked = false
        commented = false
        shared = false
        saved = false
        
        // Reset Scroll Stats for NEW reel
        scrollDistances.clear()
        
        Log.d("REEL_COUNTER", "Reel #$reelCount Started")
    }
    
    private fun processPreviousReel(now: Long) {
        val dwellMs = now - lastReelStartTime
        val dwellSec = dwellMs / 1000.0
        
        // --- Metrics Calculations ---
        
        // 1. Doomscroll (Scroll Streak)
        // If dwell < 5s, increment streak. Else reset.
        if (dwellSec < 5.0) {
            continuousScrollCount++
        } else {
            continuousScrollCount = 0
        }
        
        // 2. Rolling Stats
        dwellWindow.addLast(dwellSec)
        if (dwellWindow.size > WINDOW_SIZE) dwellWindow.removeFirst()
        
        val rollingMean = dwellWindow.average()
        val variance = if (dwellWindow.isNotEmpty()) dwellWindow.map { (it - rollingMean) * (it - rollingMean) }.average() else 0.0
        val rollingStd = sqrt(variance)

        // 3. Scroll Speed
        val avgSpeed = if (scrollDistances.isNotEmpty()) scrollDistances.average() else 0.0
        val maxSpeed = if (scrollDistances.isNotEmpty()) scrollDistances.maxOrNull() ?: 0.0 else 0.0

        // 4. Formatting
        val formattedStartTime = dateFormat.format(Date(lastReelStartTime))
        val formattedEndTime = timeFormat.format(Date(now))
        val timeOfDay = timeFormat.format(Date(now))
        val timePeriod = getTimePeriod()

        // 5. CSV Line Construction
        // Columns: SessionNum,ReelIndex,StartTime,EndTime,DwellTime,TimePeriod,AvgScrollSpeed,MaxScrollSpeed,RollingMean,RollingStd,CumulativeReels,ScrollStreak,Liked,Commented,Shared,Saved
        val line = listOf(
            currentSessionNumber,       // SessionNum
            reelCount,                  // ReelIndex
            formattedStartTime,         // StartTime
            formattedEndTime,           // EndTime
            String.format("%.2f", dwellSec), // DwellTime
            timePeriod,                 // TimePeriod
            String.format("%.2f", avgSpeed), // AvgScrollSpeed (Intensity)
            String.format("%.2f", maxSpeed), // MaxScrollSpeed (Intensity)
            String.format("%.2f", rollingMean),
            String.format("%.2f", rollingStd),
            cumulativeReels,            // CumulativeReels in Session
            continuousScrollCount,      // ScrollStreak (Doomscroll)
            liked, commented, shared, saved
        ).joinToString(",")
        
        Log.d("DWELL", "Dwell: ${dwellSec}s, Streak: $continuousScrollCount, Liked: $liked")
        appendToCsv(line)
    }

    private fun startNewSession(now: Long) {
        sessionStartTime = now
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Date Check
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val lastDate = prefs.getString("last_session_date", "")
        
        if (today != lastDate) {
            // New Day, Reset Session Count
            currentSessionNumber = 1
        } else {
            // Same Day, Increment
            val lastNum = prefs.getInt(KEY_SESSION_NUM, 0)
            currentSessionNumber = lastNum + 1
        }
        
        // Save new state
        prefs.edit()
            .putInt(KEY_SESSION_NUM, currentSessionNumber)
            .putString("last_session_date", today)
            .apply()
        
        cumulativeReels = 0
        reelCount = 0
        continuousScrollCount = 0
        dwellWindow.clear()
        
        lastActiveTime = now // initialize session timer
        lastReelDebounceTime = now // initialize debounce timer
        
        Log.d("SESSION", "Session #$currentSessionNumber started (Date: $today)")
    }
    
    private fun resetSessionTimer(now: Long) {
        lastActiveTime = now
    }

    private fun checkSessionTimeout(now: Long, packageName: String) {
        if (sessionStartTime == null) return
        
        val idleTime = now - lastActiveTime
        val isInstagram = (packageName == "com.instagram.android")
        
        if (isInstagram) {
            // Accommodate long reel watches (up to 5 mins) before forced timeout
            if (idleTime > 300_000L) {
                Log.d("SESSION", "Session ended (5 min inactivity inside Instagram)")
                endCurrentSession(now)
            }
        } else {
            // Outside Instagram - apply the strict 20s timeout
            if (idleTime > 20_000L) {
                Log.d("SESSION", "Session ended (20s outside Instagram)")
                endCurrentSession(lastActiveTime) // Use last active time to bound the final reel dwell
            }
        }
    }

    private fun endCurrentSession(endTime: Long) {
        if (sessionStartTime == null) return
        
        // Log the final reel before ending the session!
        if (lastReelStartTime != 0L && endTime > lastReelStartTime) {
            processPreviousReel(endTime)
        }
        
        // Trigger Survey Prompt
        showSurveyPrompt()
        
        sessionStartTime = null
        reelCount = 0
        cumulativeReels = 0
        continuousScrollCount = 0
        lastReelStartTime = 0L
        dwellWindow.clear()
        Log.d("SESSION", "Session Cleanup Complete")
    }

    private fun showSurveyPrompt() {
        val googleFormUrl = "https://docs.google.com/forms/d/e/1FAIpQLSfsPHRQwqJ5oq1_zeqqBHPfzPmG00aUDzMrRO0RC7YscRRP6w/viewform?usp=dialog"
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(googleFormUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with app icon if available
            .setContentTitle("Instagram Session Ended")
            .setContentText("Tap here to fill the post-session survey.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(currentSessionNumber, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Survey Request"
            val descriptionText = "Prompts to fill survey after session"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getTimePeriod(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            in 21..23 -> "Night"
            in 0..4 -> "Late Night"
            else -> "Night"
        }
    }

    private fun ensureCsvHeader() {
        val file = File(filesDir, "insta_data.csv")
        // Check if we need to write header (new file or empty)
        // Also, if schema changed (we removed timestamp), we might want to overwrite? 
        // No, that destroys data. We'll just append. User can clear data if they want cleaner headers.
        if (!file.exists() || file.length() == 0L) {
            file.writeText("SessionNum,ReelIndex,StartTime,EndTime,DwellTime,TimePeriod,AvgScrollSpeed,MaxScrollSpeed,RollingMean,RollingStd,CumulativeReels,ScrollStreak,Liked,Commented,Shared,Saved\n")
        }
    }

    private fun appendToCsv(line: String) {
        val file = File(filesDir, "insta_data.csv")
        try {
           if (!file.exists()) ensureCsvHeader()
           file.appendText(line + "\n")
        } catch (e: Exception) {
           Log.e("CSV", "Error writing csv", e)
        }
    }

    override fun onInterrupt() {
    }
}
