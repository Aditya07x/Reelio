package com.example.instatracker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object SurveyUIUtils {

    fun createMainLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
            
            // Deep sleek gradient background
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#090E17"), Color.parseColor("#152238"))
            )
        }
    }

    fun createTitleView(context: Context, titleText: String): TextView {
        return TextView(context).apply {
            text = titleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.parseColor("#0DDFF2"))
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER
            // A bit of text shadow for modern neon glow
            setShadowLayer(16f, 0f, 0f, Color.parseColor("#0DDFF2"))
        }
    }

    fun createQuestionView(context: Context, questionText: String): TextView {
        return TextView(context).apply {
            text = questionText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 0, 0, 48)
            gravity = Gravity.CENTER
            setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics), 1.0f)
        }
    }

    fun createButtonLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    fun createStyledButton(context: Context, label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            
            // Modern rounded button with glassmorphic slate background
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor("#1E293B"))
                setStroke(2, Color.parseColor("#334155"))
            }

            setOnClickListener {
                // Flash animation on click
                this.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                    this.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                        onClick()
                    }
                }.start()
            }
        }
    }
}
