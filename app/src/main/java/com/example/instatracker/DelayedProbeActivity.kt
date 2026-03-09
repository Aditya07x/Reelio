package com.example.instatracker

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager

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
        val scroll = SurveyUIUtils.createScrollRoot(this)
        val layout = SurveyUIUtils.createMainLayout(this)

        layout.addView(SurveyUIUtils.createSystemLabel(this))
        layout.addView(SurveyUIUtils.createBadge(this, "POST-SESSION  ·  REFLECTION", "#BF5AF2"))
        layout.addView(SurveyUIUtils.createTitleView(this, "An hour ago you were on Instagram. Looking back..."))
        layout.addView(SurveyUIUtils.createSubtitle(this, "HOW DOES SESSION #${sessionNum} FEEL NOW?"))
        layout.addView(SurveyUIUtils.createDivider(this))

        val options = listOf(
            Triple("I'm glad I took that break",            "😌", "#34C759"),
            Triple("It was fine, nothing to worry about",   "🙂", "#0A84FF"),
            Triple("I wish I'd stopped sooner",             "😕", "#FFB340"),
            Triple("I regret opening it at all",            "😣", "#FF9500"),
            Triple("I still feel off / distracted from it", "😩", "#FF2D55")
        )

        options.forEachIndexed { index, (label, emoji, color) ->
            layout.addView(
                SurveyUIUtils.createOptionButton(this, label, emoji, color) {
                    // 1..5 scale, higher = worse reflection.
                    val score = index + 1
                    saveDelayedRegret(score)
                }
            )
        }

        layout.addView(SurveyUIUtils.createSkipButton(this) {
            finish()
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

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
