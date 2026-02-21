package com.example.instatracker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.example.instatracker.models.ReelData
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    // Antigravity UI Tokens
    private val bgPrimary = Color.parseColor("#0B1220")
    private val cardSurface = Color.parseColor("#1A2333")
    private val cardElevated = Color.parseColor("#1F2A3D")
    private val borderSubtle = Color.parseColor("#10FFFFFF") // approx rgba(255,255,255,0.06)
    
    private val cyanAccent = Color.parseColor("#22D3EE")
    private val successAccent = Color.parseColor("#34D399")
    private val warningAccent = Color.parseColor("#FBBF24")
    private val dangerAccent = Color.parseColor("#F87171")
    
    private val textPrimary = Color.parseColor("#F3F4F6")
    private val textSecondary = Color.parseColor("#9CA3AF")
    private val textMuted = Color.parseColor("#6B7280")

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    
    // Summary Views
    private lateinit var durationText: TextView
    private lateinit var reelsText: TextView
    private lateinit var avgDwellText: TextView
    private lateinit var riskLevelText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Notification Permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                 android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
             }
        }

        val displayMetrics = resources.displayMetrics
        val dp = { value: Int -> (value * displayMetrics.density).roundToInt() }

        val rootScrollView = ScrollView(this).apply {
            setBackgroundColor(bgPrimary)
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }
        rootScrollView.addView(rootLayout)

        // SECTION 1: HEADER
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(32))
        }

        val titleView = TextView(this).apply {
            text = "Reelio"
            textSize = 32f
            setTextColor(textPrimary)
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
            gravity = Gravity.CENTER_HORIZONTAL
            // Simulated neon glow shadow
            setShadowLayer(dp(20).toFloat(), 0f, 0f, Color.parseColor("#4022D3EE"))
        }

        val subtitleView = TextView(this).apply {
            text = "CONTINUOUS LATENT STATE ENGINE"
            textSize = 10f
            setTextColor(cyanAccent)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            letterSpacing = 0.15f
            setPadding(0, dp(4), 0, 0)
        }

        headerLayout.addView(titleView)
        headerLayout.addView(subtitleView)
        rootLayout.addView(headerLayout)

        // SECTION 2: SYSTEM STATUS CARD
        val statusCard = createGlassCard(dp).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(24)) }
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusLabel = TextView(this).apply {
            text = "Accessibility Service"
            textSize = 15f
            setTextColor(textPrimary)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val indicatorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                setMargins(0, 0, dp(8), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dangerAccent) // Default red
            }
        }

        statusText = TextView(this).apply {
            text = "Offline"
            textSize = 13f
            setTextColor(dangerAccent)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        }

        indicatorContainer.addView(statusDot)
        indicatorContainer.addView(statusText)
        
        statusRow.addView(statusLabel)
        statusRow.addView(indicatorContainer)
        statusCard.addView(statusRow)

        enableButton = Button(this).apply {
            text = "Enable Accessibility"
            isAllCaps = false
            textSize = 15f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(bgPrimary)
            background = GradientDrawable().apply {
                setColor(cyanAccent)
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { setMargins(0, dp(16), 0, 0) }
            
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        statusCard.addView(enableButton)

        rootLayout.addView(statusCard)

        // SECTION 3: LIVE SESSION SUMMARY CARD
        val summaryCardLabel = TextView(this).apply {
            text = "LIVE SESSION SUMMARY"
            textSize = 11f
            setTextColor(textMuted)
            letterSpacing = 0.1f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setPadding(dp(4), 0, 0, dp(8))
        }
        rootLayout.addView(summaryCardLabel)

        val summaryCard = createGlassCard(dp).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(32)) }
        }

        val gridLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        val gridRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(20)) }
        val gridRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val durationBox = createKpiBox("Session Duration", "--:--", dp).also { durationText = it.second }
        val reelsBox = createKpiBox("Reels Observed", "0", dp).also { reelsText = it.second }
        val avgDwellBox = createKpiBox("Average Dwell", "0.0s", dp).also { avgDwellText = it.second }
        val riskBox = createKpiBox("Current Risk", "Unknown", dp).also { riskLevelText = it.second }

        gridRow1.addView(durationBox.first)
        gridRow1.addView(reelsBox.first)
        gridRow2.addView(avgDwellBox.first)
        gridRow2.addView(riskBox.first)

        gridLayout.addView(gridRow1)
        gridLayout.addView(gridRow2)
        summaryCard.addView(gridLayout)
        rootLayout.addView(summaryCard)

        // SECTION 5: PRIMARY CTA
        val dashboardButton = Button(this).apply {
            text = "View Behavioral Dashboard"
            isAllCaps = false
            textSize = 16f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(bgPrimary)
            
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#06B6D4"), cyanAccent) // darker cyan to bright cyan
            ).apply { cornerRadius = dp(26).toFloat() }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { setMargins(0, dp(8), 0, dp(16)) }
            
            stateListAnimator = null // remove default android shadow
            elevation = dp(8).toFloat()

            setOnClickListener {
                startActivity(Intent(this@MainActivity, DashboardActivity::class.java))
            }
        }
        rootLayout.addView(dashboardButton)

        // Utility Buttons (Export / Clear)
        val utilityLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val exportButton = TextView(this).apply {
            text = "Export Raw CSV"
            textSize = 13f
            setTextColor(textSecondary)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { exportCsv() }
        }
        val clearDivider = TextView(this).apply { text = "•"; setTextColor(textMuted); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val clearButton = TextView(this).apply {
            text = "Clear Data"
            textSize = 13f
            setTextColor(dangerAccent)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { showRefreshDialog() }
        }

        utilityLayout.addView(exportButton)
        utilityLayout.addView(clearDivider)
        utilityLayout.addView(clearButton)
        rootLayout.addView(utilityLayout)

        setContentView(rootScrollView)
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateLiveSummary()
    }

    private fun createGlassCard(dp: (Int) -> Int): LinearLayout {
        return LinearLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(cardSurface)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), borderSubtle)
            }
        }
    }

    private fun createKpiBox(label: String, initialValue: String, dp: (Int) -> Int): Pair<LinearLayout, TextView> {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueView = TextView(this).apply {
            text = initialValue
            textSize = 24f
            setTextColor(textPrimary)
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(textMuted)
            letterSpacing = 0.05f
            setPadding(0, dp(4), 0, 0)
        }
        container.addView(valueView)
        container.addView(labelView)
        return Pair(container, valueView)
    }

    private fun updateServiceStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val isEnabled = enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }

        if (isEnabled) {
            (statusDot.background as GradientDrawable).setColor(successAccent)
            statusText.text = "Active & Monitoring"
            statusText.setTextColor(successAccent)
            enableButton.visibility = View.GONE
        } else {
            (statusDot.background as GradientDrawable).setColor(dangerAccent)
            statusText.text = "Offline - Service Needs Permission"
            statusText.setTextColor(dangerAccent)
            enableButton.visibility = View.VISIBLE
        }
    }

    private fun updateLiveSummary() {
        val file = File(filesDir, "insta_data.csv")
        if (!file.exists()) return

        try {
            val lines = file.readLines()
            if (lines.size <= 1) return
            
            val dataLines = lines.drop(1)
            reelsText.text = dataLines.size.toString()

            var totalDwell = 0.0
            dataLines.forEach { line ->
                val tokens = line.split(",")
                if (tokens.size >= 5) {
                    totalDwell += tokens[4].toDoubleOrNull() ?: 0.0
                }
            }

            val avg = if (dataLines.isNotEmpty()) totalDwell / dataLines.size else 0.0
            avgDwellText.text = String.format("%.1fs", avg)
            
            val minutes = (totalDwell / 60).toInt()
            val seconds = (totalDwell % 60).toInt()
            durationText.text = String.format("%02d:%02d", minutes, seconds)

            // Extremely basic risk indication for landing page based on avg dwell
            when {
                avg > 8.0 -> {
                    riskLevelText.text = "Captured"
                    riskLevelText.setTextColor(dangerAccent)
                }
                avg > 4.0 -> {
                    riskLevelText.text = "Drifting"
                    riskLevelText.setTextColor(warningAccent)
                }
                else -> {
                    riskLevelText.text = "Stable"
                    riskLevelText.setTextColor(successAccent)
                }
            }

        } catch (e: Exception) {
            Log.e("SummaryUpdate", "Error reading stats", e)
        }
    }

    private fun showRefreshDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Behavioral Data")
            .setMessage("Are you sure you want to permanently delete all tracked UI events? The behavioral model will reset.")
            .setPositiveButton("Delete Data") { _, _ -> clearData() }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    private fun clearData() {
        val file = File(filesDir, "insta_data.csv")
        if (file.exists()) file.delete()
        
        reelsText.text = "0"
        avgDwellText.text = "0.0s"
        durationText.text = "--:--"
        riskLevelText.text = "Unknown"
        riskLevelText.setTextColor(textPrimary)
    }

    private fun exportCsv() {
        val file = File(filesDir, "insta_data.csv")
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Behavioral Baseline Data"))
    }
}
