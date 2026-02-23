package com.example.instatracker

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import android.graphics.Color
import android.view.Gravity
import kotlin.random.Random

class MicroProbeActivity : Activity() {
    private var postSessionRating = 0
    private var regretScore = 0
    private var moodAfter = 0
    
    // Loaded from Pre-Session Intention
    private var moodBefore = 0
    private var intendedAction = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("InstaTrackerPrefs", MODE_PRIVATE)
        moodBefore = prefs.getInt("current_mood_before", 0)
        intendedAction = prefs.getString("current_intended_action", "") ?: ""
        

        showPostSessionRating()
    }

    private fun showPostSessionRating() {
        val layout = SurveyUIUtils.createMainLayout(this)
        val title = SurveyUIUtils.createTitleView(this, "Reelio Post-Session")
        val question = SurveyUIUtils.createQuestionView(this, "How do you feel after this session? (1-5)")
        val buttonsLayout = SurveyUIUtils.createButtonLayout(this)
        
        for (i in 1..5) {
            val btn = SurveyUIUtils.createStyledButton(this, i.toString()) {
                postSessionRating = i
                showMoodAfterPrompt()
            }
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { setMargins(4, 0, 4, 0) }
            buttonsLayout.addView(btn, params)
        }
        
        layout.addView(title)
        layout.addView(question)
        layout.addView(buttonsLayout)
        setContentView(layout)
    }

    private fun showMoodAfterPrompt() {
        val layout = SurveyUIUtils.createMainLayout(this)
        val question = SurveyUIUtils.createQuestionView(this, "Rate your mood right now (1-5)")
        val buttonsLayout = SurveyUIUtils.createButtonLayout(this)
        
        for (i in 1..5) {
            val btn = SurveyUIUtils.createStyledButton(this, i.toString()) {
                moodAfter = i
                showRegretPrompt()
            }
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { setMargins(4, 0, 4, 0) }
            buttonsLayout.addView(btn, params)
        }
        
        layout.addView(question)
        layout.addView(buttonsLayout)
        setContentView(layout)
    }

    private fun showRegretPrompt() {
        val layout = SurveyUIUtils.createMainLayout(this)
        val question = SurveyUIUtils.createQuestionView(this, "Did you mean to scroll that long? (1=No, 5=Yes)")
        val buttonsLayout = SurveyUIUtils.createButtonLayout(this)
        
        for (i in 1..5) {
            val btn = SurveyUIUtils.createStyledButton(this, i.toString()) {
                regretScore = 6 - i // Invert so higher regret = higher score
                finalizeProbe()
            }
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { setMargins(4, 0, 4, 0) }
            buttonsLayout.addView(btn, params)
        }
        
        layout.addView(question)
        layout.addView(buttonsLayout)
        setContentView(layout)
    }

    private fun finalizeProbe() {
        val moodDelta = moodAfter - moodBefore
        // Dummy check for actual vs intended, assuming "Killing Time" matches anything, "Specific Search" fails if dwell > 10m
        val actualVsIntendedMatch = if (intendedAction == "Killing Time") 1 else 0
        
        val prefs = getSharedPreferences("InstaTrackerPrefs", MODE_PRIVATE)
        prefs.edit()
            .putInt("probe_post_rating", postSessionRating)
            .putInt("probe_regret_score", regretScore)
            .putInt("probe_mood_after", moodAfter)
            .putInt("probe_mood_delta", moodDelta)
            .putInt("probe_actual_vs_intended", actualVsIntendedMatch)
            .apply()
            
        // Tell Accessibility service to flush this to CSV 
        // (Wait, actually we can just format the string and save it here since Service is background)
        val sessionNum = prefs.getInt("session_number", -1)
        val probeLine = "$postSessionRating,$intendedAction,$actualVsIntendedMatch,$regretScore,$moodBefore,$moodAfter,$moodDelta"
        prefs.edit().putString("last_microprobe_result", probeLine).apply()
        
        val file = File(filesDir, "probe_data.csv")
        if (!file.exists()) {
            file.writeText("Timestamp,SessionNum,Rating,Action,Match,Regret,MoodBefore,MoodAfter,Delta\n")
        }
        file.appendText("${System.currentTimeMillis()},$sessionNum,$probeLine\n")
        
        finish()
    }
}
