package com.example.instatracker

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import kotlin.random.Random

// Triggered at Session START (sampled at ~30%)
class IntentionProbeActivity : Activity() {
    
    private var moodBefore = 0
    private var intendedAction = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        

        showMoodPrompt()
    }
    
    private fun showMoodPrompt() {
        val layout = SurveyUIUtils.createMainLayout(this)
        val title = SurveyUIUtils.createTitleView(this, "Reelio Pre-Session")
        val question = SurveyUIUtils.createQuestionView(this, "Rate your mood right now (1-5)")
        val buttonsLayout = SurveyUIUtils.createButtonLayout(this)
        
        for (i in 1..5) {
            val btn = SurveyUIUtils.createStyledButton(this, i.toString()) {
                moodBefore = i
                showIntentionPrompt()
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

    private fun showIntentionPrompt() {
        val layout = SurveyUIUtils.createMainLayout(this)
        val question = SurveyUIUtils.createQuestionView(this, "What do you want to do?")
        
        val options = listOf("Browse", "Specific Search", "Habit", "Killing Time")
        layout.addView(question)
        
        for (opt in options) {
            val btn = SurveyUIUtils.createStyledButton(this, opt) {
                intendedAction = opt
                savePreSessionData()
                finish()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            layout.addView(btn, params)
        }
        
        setContentView(layout)
    }

    private fun savePreSessionData() {
        val prefs = getSharedPreferences("InstaTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("current_mood_before", moodBefore)
            .putString("current_intended_action", intendedAction)
            .apply()
    }
}
