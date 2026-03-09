package com.example.instatracker

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

// Triggered at Session START (sampled at ~30%)
class IntentionProbeActivity : Activity() {

    private var moodBefore = 0
    private var previousContext = "unknown"
    private var intendedAction = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Translucent status bar for immersive feel
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#05050A")

        showMoodPrompt()
    }

    // ── Step 1: Stress/Restlessness State ─────────────────────────────────────
    private fun showMoodPrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 3, currentStep = 1))
        layout.addView(SurveyUIUtils.createBadge(this, "PRE-SESSION  ·  STATE CHECK", "#0DDFF2"))
        layout.addView(SurveyUIUtils.createTitleView(this, "Right now I feel..."))
        layout.addView(SurveyUIUtils.createSubtitle(this, "SELECT YOUR CURRENT STATE"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val stressOptions = listOf(
            Triple("Calm and focused", "😌", "#34C759") to 1,
            Triple("A bit restless or bored", "😶", "#0A84FF") to 6,
            Triple("Stressed or overwhelmed", "😤", "#FF2D55") to 10,
            Triple("Tired / winding down", "🥱", "#BF5AF2") to 7,
            Triple("Fine, just taking a break", "☕", "#FFB340") to 2
        )

        for ((choice, encodedRisk) in stressOptions) {
            val (label, emoji, color) = choice
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    // Encoded risk scale for Python: 1->0.0, 2->0.1, 6->0.6, 7->0.7, 10->1.0
                    moodBefore = encodedRisk
                    showContextPrompt()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            moodBefore = 0
            showContextPrompt()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Step 2: Previous Context ─────────────────────────────────────────────
    private fun showContextPrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 3, currentStep = 2))
        layout.addView(SurveyUIUtils.createBadge(this, "PRE-SESSION  ·  CONTEXT", "#0DDFF2"))
        layout.addView(SurveyUIUtils.createTitleView(this, "What were you just doing?"))
        layout.addView(SurveyUIUtils.createSubtitle(this, "REPLACES UNRELIABLE USAGESTATS DETECTION"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val contexts = listOf(
            Triple("Work / Study", "💼", "#0DDFF2"),
            Triple("Socializing",  "💬", "#BF5AF2"),
            Triple("Relaxing",     "🧘", "#34C759"),
            Triple("Chores / Task", "🧹", "#FFB340"),
            Triple("Just woke up", "😴", "#AF52DE"),
            Triple("Boredom",      "😶", "#6B7A9F")
        )

        for ((label, emoji, color) in contexts) {
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    previousContext = label
                    showIntentionPrompt()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            previousContext = "unknown"
            showIntentionPrompt()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Step 3: Intention ─────────────────────────────────────────────────────
    private fun showIntentionPrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 3, currentStep = 3))
        layout.addView(SurveyUIUtils.createBadge(this, "PRE-SESSION  ·  INTENTION", "#0DDFF2"))
        layout.addView(SurveyUIUtils.createTitleView(this, "Why are you opening this?"))
        layout.addView(SurveyUIUtils.createSubtitle(this, "THE ALGORITHM WILL TRACK YOUR ACTUAL VS INTENDED"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val options = listOf(
            Triple("Bored / Nothing to do",     "😶", "#0DDFF2"),
            Triple("Stressed / Avoidance",      "😤", "#FF2D55"),
            Triple("Procrastinating something", "⏰", "#FF9500"),
            Triple("Habit / Automatic",         "🔁", "#BF5AF2"),
            Triple("Quick break (intentional)", "☕", "#FFB340"),
        )

        for ((label, emoji, color) in options) {
            layout.addView(
                SurveyUIUtils.createOptionButton(
                    context     = this,
                    label       = label,
                    emoji       = emoji,
                    accentColor = color
                ) {
                    intendedAction = label
                    saveAndFinish()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            intendedAction = ""
            saveAndFinish()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Persist + close ───────────────────────────────────────────────────────
    private fun saveAndFinish() {
        getSharedPreferences("InstaTrackerPrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("current_mood_before", moodBefore)
            .putString("previous_context", previousContext)
            .putString("current_intended_action", intendedAction)
            .putLong("intention_session_timestamp", System.currentTimeMillis())
            .apply()
        finish()
    }
}