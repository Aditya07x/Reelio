package com.example.instatracker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object SurveyUIUtils {

    // Professional design tokens: high legibility, subtle depth, restrained accents.
    private const val BG_TOP = "#0B111A"
    private const val BG_MID = "#0E1522"
    private const val BG_BOTTOM = "#070A11"
    private const val SURFACE = "#121A2A"
    private const val SURFACE_ALT = "#0F1724"
    private const val PRIMARY = "#5DD4BE"
    private const val MAGENTA = "#C85BEA"
    private const val WARNING = "#F4B942"
    private const val TEXT = "#EEF3FF"
    private const val TEXT_DIM = "#A2B0C8"
    private const val TEXT_FAINT = "#77839A"
    private const val BORDER = "#FFFFFF22"
    private const val TRACK = "#FFFFFF18"

    private fun c(hex: String) = Color.parseColor(hex)
    private fun tint(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    // ── Root scroll container ─────────────────────────────────────────────────
    fun createScrollRoot(context: Context): ScrollView {
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(c(BG_TOP), c(BG_MID), c(BG_BOTTOM))
        )
        return ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = bg
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    // ── Main content layout ───────────────────────────────────────────────────
    fun createMainLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val hPad = dp(context, 22f)
            setPadding(hPad, dp(context, 48f), hPad, dp(context, 34f))

            alpha = 0f
            translationY = dp(context, 10f).toFloat()
            post {
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(260)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    // ── Top system label  e.g. "REELIO // ALSE" ──────────────────────────────
    fun createSystemLabel(context: Context): TextView {
        return TextView(context).apply {
            text = "REELIO  //  SESSION CHECK-IN"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(c(TEXT_FAINT))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 24f)
            layoutParams = lp
        }
    }

    // ── Step indicator dots ───────────────────────────────────────────────────
    fun createStepIndicator(context: Context, totalSteps: Int, currentStep: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 24f)
            layoutParams = lp

            for (i in 1..totalSteps) {
                val isActive = i == currentStep
                val isDone = i < currentStep
                val accent = if (isActive) c(PRIMARY) else c(TEXT_DIM)

                val segment = View(context).apply {
                    val width = if (isActive) dp(context, 34f) else dp(context, 10f)
                    val segLp = LinearLayout.LayoutParams(width, dp(context, 6f))
                    segLp.setMargins(dp(context, 4f), 0, dp(context, 4f), 0)
                    layoutParams = segLp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(context, 4f).toFloat()
                        when {
                            isActive -> setColor(tint(accent, 240))
                            isDone -> setColor(tint(c(PRIMARY), 130))
                            else -> setColor(c(TRACK))
                        }
                    }
                }
                addView(segment)
            }
        }
    }

    // ── Survey type badge  e.g. "PRE-SESSION" ────────────────────────────────
    fun createBadge(context: Context, label: String, color: String = PRIMARY): TextView {
        val accent = c(color.take(7))
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(tint(accent, 245))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            val hPad = dp(context, 12f)
            val vPad = dp(context, 6f)
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 999f).toFloat()
                setColor(tint(accent, 24))
                setStroke(1, tint(accent, 110))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = dp(context, 16f)
            layoutParams = lp
        }
    }

    // ── Title ─────────────────────────────────────────────────────────────────
    fun createTitleView(context: Context, titleText: String): TextView {
        return TextView(context).apply {
            text = titleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(c(TEXT))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.01f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 8f)
            layoutParams = lp
        }
    }

    // ── Question text ─────────────────────────────────────────────────────────
    fun createQuestionView(context: Context, questionText: String): TextView {
        return TextView(context).apply {
            text = questionText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(c(TEXT_DIM))
            gravity = Gravity.CENTER
            setLineSpacing(dp(context, 4f).toFloat(), 1f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 28f)
            layoutParams = lp
        }
    }

    // ── Divider line ──────────────────────────────────────────────────────────
    fun createDivider(context: Context): View {
        return View(context).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 1f)
            )
            lp.bottomMargin = dp(context, 22f)
            layoutParams = lp
            setBackgroundColor(c(BORDER))
        }
    }

    // ── Horizontal button row (for 1-5 numeric ratings) ───────────────────────
    fun createButtonLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 14f)
            layoutParams = lp
        }
    }

    // ── Numeric rating button (glassy card style) ─────────────────────────────
    fun createStyledButton(context: Context, label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(c(TEXT))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 14f), 0, dp(context, 14f))

            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(c(SURFACE), c(SURFACE_ALT))
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 14f).toFloat()
                setStroke(1, c(BORDER))
            }

            setOnClickListener {
                // Neon flash on tap
                animateButtonTap(this, label.toIntOrNull() ?: 0, onClick)
            }
        }
    }

    // ── Vertical option button (professional card style) ─────────────────────
    fun createOptionButton(
        context: Context,
        label: String,
        emoji: String = "",
        accentColor: String = PRIMARY,
        onClick: () -> Unit
    ): LinearLayout {
        val accent = c(accentColor.take(7))

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dp(context, 14f)
            val vPad = dp(context, 14f)
            setPadding(hPad, vPad, hPad, vPad)

            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tint(accent, 26), c(SURFACE_ALT))
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 16f).toFloat()
                setStroke(1, tint(accent, 110))
            }

            elevation = dp(context, 1f).toFloat()

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 10f)
            layoutParams = lp

            val accentBar = View(context).apply {
                val barLp = LinearLayout.LayoutParams(dp(context, 3f), dp(context, 22f))
                barLp.rightMargin = dp(context, 12f)
                layoutParams = barLp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(context, 2f).toFloat()
                    setColor(tint(accent, 220))
                }
            }

            val textView = TextView(context).apply {
                // Keep copy clean and professional by rendering plain labels.
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(c(TEXT))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val arrow = TextView(context).apply {
                text = ">"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(tint(accent, 180))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            addView(accentBar)
            addView(textView)
            addView(arrow)

            setOnClickListener {
                animateOptionTap(this, accentColor, onClick)
            }
        }
    }

    // ── Mood scale labels (below rating buttons) ──────────────────────────────
    fun createMoodScaleLabels(context: Context, leftLabel: String, rightLabel: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(context, 6f)
            lp.bottomMargin = dp(context, 4f)
            layoutParams = lp

            val leftTv = TextView(context).apply {
                text = leftLabel
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(c(TEXT_FAINT))
                letterSpacing = 0.05f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val rightTv = TextView(context).apply {
                text = rightLabel
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(c(TEXT_FAINT))
                letterSpacing = 0.05f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(leftTv)
            addView(rightTv)
        }
    }

    // ── Section subtitle ──────────────────────────────────────────────────────
    fun createSubtitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(c(TEXT_DIM))
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(context, 18f)
            layoutParams = lp
        }
    }

    // ── Skip button ───────────────────────────────────────────────────────────
    fun createSkipButton(context: Context, onSkip: () -> Unit): TextView {
        return TextView(context).apply {
            text = "Skip for now"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(c(TEXT_DIM))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.03f
            gravity = Gravity.CENTER

            val hPad = dp(context, 12f)
            val vPad = dp(context, 8f)
            setPadding(hPad, vPad, hPad, vPad)

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 999f).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(1, c(BORDER))
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = dp(context, 18f)
            layoutParams = lp
            setOnClickListener { onSkip() }
        }
    }

    // ── Animation: numeric button tap with subtle depth feedback ─────────────
    private fun animateButtonTap(view: TextView, value: Int, onClick: () -> Unit) {
        val accentHex = when {
            value <= 2 -> PRIMARY
            value == 3 -> WARNING
            else -> MAGENTA
        }
        val accent = c(accentHex)

        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(70)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(tint(accent, 34), c(SURFACE_ALT))
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(view.context, 14f).toFloat()
                    setStroke(2, tint(accent, 210))
                }
                view.setTextColor(c(TEXT))

                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { onClick() }
                    .start()
            }
            .start()
    }

    // ── Animation: option button tap ──────────────────────────────────────────
    private fun animateOptionTap(view: LinearLayout, accentColor: String, onClick: () -> Unit) {
        val accent = c(accentColor.take(7))
        view.animate()
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(70)
            .withEndAction {
                view.background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(tint(accent, 40), c(SURFACE_ALT))
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(view.context, 16f).toFloat()
                    setStroke(2, tint(accent, 190))
                }
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110)
                    .withEndAction { onClick() }
                    .start()
            }
            .start()
    }

    // ── Pulse animation helper (for active dot indicators) ───────────────────
    fun startPulseAnimation(view: View): ValueAnimator {
        return ValueAnimator.ofFloat(1f, 0.55f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { view.alpha = it.animatedValue as Float }
            start()
        }
    }
}