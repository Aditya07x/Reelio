package com.example.instatracker

import android.content.Context
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class MicroProbeActivity : Activity() {

    private var postSessionRating = 0
    private var regretScore = 0
    private var moodAfter = 0
    private var comparativeRating = 0

    // Loaded from pre-session
    private var moodBefore = 0
    private var intendedAction = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#05050A")

        val prefs = getSharedPreferences("InstaTrackerPrefs", Context.MODE_PRIVATE)
        val intentionTs = prefs.getLong("intention_session_timestamp", 0L)
        val intentionIsStale = System.currentTimeMillis() - intentionTs > 4 * 60 * 60 * 1000L

        moodBefore    = if (intentionIsStale) 0 else prefs.getInt("current_mood_before", 0)
        intendedAction = if (intentionIsStale) "" else prefs.getString("current_intended_action", "") ?: ""

        showSessionRatingPrompt()
    }

    // ── Step 1: Post-Session Affective State ──────────────────────────────────
    private fun showSessionRatingPrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 4, currentStep = 1))
        layout.addView(SurveyUIUtils.createBadge(this, "POST-SESSION  ·  REVIEW", "#F20DA6"))
        layout.addView(SurveyUIUtils.createTitleView(this, "Right now, after closing Instagram, I feel..."))
        layout.addView(SurveyUIUtils.createSubtitle(this, "SELECT YOUR CURRENT STATE"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val affectiveOptions = listOf(
            Triple("Refreshed / entertained",     "😌", "#34C759"),
            Triple("About the same as before",    "😐", "#0DDFF2"),
            Triple("A little drained",            "😕", "#FFB340"),
            Triple("Regret I opened it",          "😬", "#FF9500"),
            Triple("Worse than before I opened it", "😩", "#FF2D55")
        )

        for ((index, triple) in affectiveOptions.withIndex()) {
            val (label, emoji, color) = triple
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    postSessionRating = 5 - index  // Best=5, Worst=1
                    showMoodAfterPrompt()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            postSessionRating = 0
            showMoodAfterPrompt()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Step 2: Focus Change (Before/After Comparison) ────────────────────────
    private fun showMoodAfterPrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 4, currentStep = 2))
        layout.addView(SurveyUIUtils.createBadge(this, "POST-SESSION  ·  FOCUS CHECK", "#F20DA6"))
        layout.addView(SurveyUIUtils.createTitleView(this, "Compared to before you opened Instagram, your focus is..."))
        layout.addView(SurveyUIUtils.createSubtitle(this, "HOW DID YOUR FOCUS CHANGE?"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val focusOptions = listOf(
            Triple("Better or the same", "⚡", "#34C759"),
            Triple("A bit worse",        "😕", "#FFB340"),
            Triple("Much worse / scattered", "🌫️", "#FF2D55")
        )

        for ((index, triple) in focusOptions.withIndex()) {
            val (label, emoji, color) = triple
            val focusScore = when (index) {
                0 -> 5  // Better/same
                1 -> 3  // A bit worse
                2 -> 1  // Much worse
                else -> 0
            }
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    moodAfter = focusScore
                    showRegretPrompt()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            moodAfter = 0
            showRegretPrompt()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Step 3: Regret / Volition ─────────────────────────────────────────────
    private fun showRegretPrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 4, currentStep = 3))
        layout.addView(SurveyUIUtils.createBadge(this, "POST-SESSION  ·  VOLITION", "#F20DA6"))
        layout.addView(SurveyUIUtils.createTitleView(this, "Did you mean to scroll that long?"))

        // Show intention context if captured
        val subtitle = when {
            intendedAction == "Stressed / Avoidance" ->
                "YOU OPENED THIS TO AVOID SOMETHING"
            intendedAction == "Procrastinating something" ->
                "YOU OPENED THIS TO PROCRASTINATE"
            intendedAction == "Quick break (intentional)" ->
                "YOU PLANNED A QUICK BREAK"
            intendedAction == "Habit / Automatic" ->
                "YOU SAID THIS WAS AUTOMATIC"
            intendedAction.isNotEmpty() ->
                "YOU OPENED THIS: ${intendedAction.uppercase()}"
            else ->
                "WAS THIS SESSION INTENTIONAL?"
        }
        layout.addView(SurveyUIUtils.createSubtitle(this, subtitle))
        layout.addView(SurveyUIUtils.createDivider(this))

        val options = listOf(
            Triple("Definitely not",  "😤", "#FF2D55"),
            Triple("Not really",      "😬", "#FFB340"),
            Triple("Somewhat",        "😐", "#D0DCF0"),
            Triple("Pretty much",     "🙂", "#0A84FF"),
            Triple("Completely yes",  "✅", "#0DDFF2"),
        )

        options.forEachIndexed { index, (label, emoji, color) ->
            layout.addView(
                SurveyUIUtils.createOptionButton(
                    context     = this,
                    label       = label,
                    emoji       = emoji,
                    accentColor = color
                ) {
                    // Invert: "Definitely not" = highest regret score (5)
                    regretScore = 5 - index
                    showComparativePrompt()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            regretScore = 0
            showComparativePrompt()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Step 4: Comparative ──────────────────────────────────────────────────
    private fun showComparativePrompt() {
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createStepIndicator(this, totalSteps = 4, currentStep = 4))
        layout.addView(SurveyUIUtils.createBadge(this, "POST-SESSION  ·  EXPERIENCE", "#F20DA6"))
        layout.addView(SurveyUIUtils.createTitleView(this, "This session was..."))
        layout.addView(SurveyUIUtils.createSubtitle(this, "HOW DID THIS SESSION FEEL OVERALL?"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val options = listOf(
            Triple("Intentional - I got what I came for", "✅", "#34C759"),
            Triple("Okay, nothing special",               "🙂", "#0A84FF"),
            Triple("Longer than I wanted",                "😕", "#FFB340"),
            Triple("A waste of time",                     "😬", "#FF9500"),
            Triple("I could not stop - it took over",     "😵", "#FF2D55")
        )

        options.forEachIndexed { index, (label, emoji, color) ->
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    // 1..5 scale, higher = worse; 0 remains reserved for skip/no response.
                    comparativeRating = index + 1
                    finalizeProbe()
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            comparativeRating = 0
            finalizeProbe()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ── Save + close ──────────────────────────────────────────────────────────
    private fun finalizeProbe() {
        val actualMatch = when {
            intendedAction.isEmpty()              -> 0
            intendedAction == "Habit / Automatic" -> 1   // no stated intent to violate
            intendedAction == "Stressed / Avoidance" -> 0 // opening to avoid is inherently low-match
            intendedAction == "Procrastinating something" -> 0 // explicit avoidance intent
            regretScore >= 4                      -> 0   // high regret = didn't match
            regretScore <= 2                      -> 1   // low regret = matched
            else                                  -> 2
        }

        val consolidatedResult = listOf(
            postSessionRating,
            intendedAction,
            actualMatch,
            regretScore,
            moodBefore,
            moodAfter,
            0, // moodDelta intentionally disabled: pre-state and post-focus now use different scales
            comparativeRating
        ).joinToString(",")

        getSharedPreferences("InstaTrackerPrefs", MODE_PRIVATE)
            .edit()
            .putInt("probe_post_rating",       postSessionRating)
            .putInt("probe_regret_score",      regretScore)
            .putInt("probe_focus_after",       moodAfter)   // renamed: now captures focus, not mood
            .putInt("probe_mood_delta",        0)
            .putInt("probe_actual_vs_intended", actualMatch)
            .putInt("comparative_rating",       comparativeRating)
            .putString("last_microprobe_result", consolidatedResult)
            .apply()

        finish()
    }

    // ── Emoji rating row ──────────────────────────────────────────────────────
    private fun buildEmojiRatingRow(
        emojis: List<String>,
        sublabels: List<String>,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
            ).toInt()
            layoutParams = lp
        }

        emojis.forEachIndexed { index, emoji ->
            val value = index + 1
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val cellLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                cellLp.setMargins(5, 0, 5, 0)
                layoutParams = cellLp
            }

            val emojiTv = TextView(this).apply {
                text = emoji
                textSize = 22f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 8 }
            }

            val btn = SurveyUIUtils.createStyledButton(this, value.toString()) {
                onSelect(value)
            }
            btn.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val subTv = TextView(this).apply {
                text = sublabels[index]
                textSize = 8f
                setTextColor(Color.parseColor("#6B7A9F"))
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 6
                layoutParams = lp
            }

            cell.addView(emojiTv)
            cell.addView(btn)
            cell.addView(subTv)
            row.addView(cell)
        }

        return row
    }
}