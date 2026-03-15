package com.example.instatracker

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class DelayedProbeActivity : Activity() {

    private var sessionNum = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionNum = intent.getIntExtra("session_num", -1)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#05050A")

        showDelayedRegretPrompt()
    }

    private fun showDelayedRegretPrompt() {
        val prefs = getSharedPreferences("InstaTrackerPrefs", Context.MODE_PRIVATE)

        // ── Gather session context from SharedPrefs ─────────────────────────
        val sessionEndMs   = prefs.getLong("last_session_end", 0L)
        val doomScore      = prefs.getFloat("last_session_doom_score", -1f)
        val reelCount      = prefs.getInt("longest_session_today_reels", 0) // best proxy available
        val timeSinceEndMs = if (sessionEndMs > 0L) System.currentTimeMillis() - sessionEndMs else 0L

        // Format the "session ended X ago" string
        val agoStr = when {
            timeSinceEndMs <= 0L         -> "about an hour ago"
            timeSinceEndMs < 90 * 60_000 -> "${(timeSinceEndMs / 60_000)}m ago"
            else                         -> "${(timeSinceEndMs / 3_600_000.0).let { "%.1f".format(it) }}h ago"
        }

        // Format a human-readable session end time, e.g. "at 9:45 PM"
        val sessionEndTimeStr = if (sessionEndMs > 0L) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            "at ${sdf.format(Date(sessionEndMs))}"
        } else {
            ""
        }

        // Build a context summary line users can actually parse
        val contextLines = buildList {
            if (sessionNum > 0) add("Session #$sessionNum")
            if (sessionEndMs > 0L) add("ended $agoStr  $sessionEndTimeStr".trim())
            if (reelCount > 0)     add("$reelCount reels viewed")
            if (doomScore >= 0f) {
                val pct = (doomScore * 100).roundToInt()
                val label = when {
                    doomScore >= 0.65f -> "⚠️ High engagement pull ($pct%)"
                    doomScore >= 0.35f -> "Moderate engagement pull ($pct%)"
                    else               -> "Low engagement pull ($pct%)"
                }
                add(label)
            }
        }
        val contextSummary = contextLines.joinToString("  ·  ")

        // ── Build UI ────────────────────────────────────────────────────────
        val (frame, scroll) = SurveyUIUtils.createRootWithBlobs(this, BlobBackgroundView.Palette.POST)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createBadge(this, "POST-SESSION  ·  REFLECTION", "#BF5AF2"))

        // Main question — concrete, actionable, user-friendly
        layout.addView(SurveyUIUtils.createTitleView(
            this,
            "Now that you've had some time to step away — how do you feel about that session?"
        ))

        // Context row: session number, time, reel count, doom score
        if (contextSummary.isNotBlank()) {
            layout.addView(createContextCard(contextSummary))
        }

        layout.addView(SurveyUIUtils.createDivider(this))

        // ── Response options ─────────────────────────────────────────────────
        val options = listOf(
            Triple("I'm glad I took that break",             "😌", "#34C759"),
            Triple("It was fine, no regrets",                "🙂", "#0A84FF"),
            Triple("I wish I'd stopped a bit sooner",        "😕", "#FFB340"),
            Triple("I regret opening Instagram at all",      "😣", "#FF9500"),
            Triple("I still feel off / distracted from it",  "😩", "#FF2D55")
        )

        // Track the child index where cards begin so staggerCards knows where to start
        val firstCardIndex = layout.childCount

        options.forEachIndexed { index, (label, emoji, color) ->
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    val score = index + 1   // 1..5 scale, higher = worse reflection
                    saveDelayedRegret(score)
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) { finish() })

        scroll.addView(layout)
        setContentView(frame)

        // ── Animate cards in — FIX: staggerCards was never called, leaving
        //    all option cards stuck at alpha=0 (invisible) until a touch event
        //    happened to trigger the card-tap animation path. ─────────────────
        layout.post {
            SurveyUIUtils.staggerCards(layout, firstCardIndex, options.size)
        }
    }

    /** A tinted info row that summarises the session the user is reflecting on. */
    private fun createContextCard(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(Color.parseColor("#6A5E56"))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setLineSpacing(dpF(4f), 1f)

            val hPad = dp(16f)
            val vPad = dp(10f)
            setPadding(hPad, vPad, hPad, vPad)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16f)
            layoutParams = lp
        }
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun dpF(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun saveDelayedRegret(score: Int) {
        if (sessionNum == -1) {
            finish()
            return
        }
        val prefs = getSharedPreferences("InstaTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("delayed_regret_score_${sessionNum}", score)
            .apply()

        // Apply delayed label to model state without re-running full HMM
        Thread {
            try {
                synchronized(InstaAccessibilityService.GLOBAL_PYTHON_LOCK) {
                    if (!com.chaquo.python.Python.isStarted()) {
                        com.chaquo.python.Python.start(
                            com.chaquo.python.android.AndroidPlatform(this)
                        )
                    }
                    val py = com.chaquo.python.Python.getInstance()
                    val module = py.getModule("reelio_alse")
                    val statePath = java.io.File(filesDir, "alse_model_state.json").absolutePath
                    val comp = prefs.getInt("comparative_rating", 0)
                    module.callAttr("apply_delayed_label", statePath, score, comp)
                    android.util.Log.d("ALSE", "Delayed label applied: regret=$score comp=$comp")
                }
            } catch (t: Throwable) {
                android.util.Log.e("ALSE", "Delayed label failed: ${t.message}")
            }
        }.start()

        finish()
    }
}
