
package com.example.instatracker

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.example.instatracker.models.ReelData
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var dataContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Notification Permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                 android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
             }
        }

        // Root Layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val titleView = TextView(this).apply {
            text = "Instagram Tracker Data"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(titleView)
        
        // Enable Access Button
        val enableButton = Button(this).apply {
            text = "Enable Accessibility"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        rootLayout.addView(enableButton)

        // View Dashboard Button
        val dashboardButton = Button(this).apply {
            text = "View Analytics Dashboard"
            setBackgroundColor(android.graphics.Color.parseColor("#22D3EE")) // Theme cyan
            setTextColor(android.graphics.Color.BLACK)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DashboardActivity::class.java))
            }
        }
        rootLayout.addView(dashboardButton)

        // Buttons Layout
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 32, 0, 32)
        }

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { showRefreshDialog() }
        }
        
        val exportButton = Button(this).apply {
            text = "Export CSV"
            setOnClickListener { exportCsv() }
        }

        buttonLayout.addView(refreshButton)
        buttonLayout.addView(android.view.View(this).apply { 
             layoutParams = LinearLayout.LayoutParams(32, 1) 
        })
        buttonLayout.addView(exportButton)
        rootLayout.addView(buttonLayout)

        // Scroll View for Data
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        dataContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(dataContainer)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)

        // Initial Load
        loadData()
    }

    private fun showRefreshDialog() {
        AlertDialog.Builder(this)
            .setTitle("Refresh Data")
            .setMessage("Do you want to just refresh the view, or CLEAR all stored data?")
            .setPositiveButton("Refresh View") { _, _ -> 
                loadData() 
            }
            .setNegativeButton("CLEAR DATA") { _, _ ->
                clearData()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    private fun clearData() {
        val file = File(filesDir, "insta_data.csv")
        if (file.exists()) {
            file.delete()
        }
        loadData()
    }

    private fun loadData() {
        dataContainer.removeAllViews()
        val dataList = loadCsvData()

        if (dataList.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No data found yet."
                setPadding(0, 16, 0, 16)
            }
            dataContainer.addView(emptyView)
        } else {
            for (data in dataList) {
                val itemView = createReelItemView(data)
                dataContainer.addView(itemView)
            }
        }
    }

    private fun createReelItemView(data: ReelData): android.view.View {
        val cardLayout = LinearLayout(this).apply {
             orientation = LinearLayout.VERTICAL
             setPadding(24, 24, 24, 24)
             background = android.graphics.drawable.GradientDrawable().apply {
                 setColor(android.graphics.Color.WHITE)
                 setStroke(2, android.graphics.Color.LTGRAY)
                 cornerRadius = 8f
             }
             layoutParams = LinearLayout.LayoutParams(
                 LinearLayout.LayoutParams.MATCH_PARENT,
                 LinearLayout.LayoutParams.WRAP_CONTENT
             ).apply {
                 setMargins(0, 0, 0, 16)
             }
             elevation = 4f
        }

        val title = TextView(this).apply {
            text = "Session #${data.sessionId} - Reel #${data.reelIndex}\n[${data.timeOfDay}]"
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val details = TextView(this).apply {
            text = "Dwell: ${data.dwellTime}s | Mean: ${data.rollingMean}s\nScrollStreak: ${data.rollingStd}?" // repurposing field for display
        }

        cardLayout.addView(title)
        cardLayout.addView(details)
        return cardLayout
    }

    private fun loadCsvData(): List<ReelData> {
        val file = File(filesDir, "insta_data.csv")
        if (!file.exists()) return emptyList()

        val list = mutableListOf<ReelData>()
        try {
            val lines = file.readLines()
            if (lines.isEmpty()) return emptyList()
            
            val header = lines[0]
            val hasNewSchema = header.contains("ScrollStreak")
            
            // Skip header
            val dataLines = if (header.startsWith("SessionNum")) lines.drop(1) else lines

            for (line in dataLines) {
                val tokens = line.split(",")
                // Schema: SessionNum,ReelIndex,StartTime,EndTime,DwellTime,TimePeriod,AvgSpeed,MaxSpeed,Mean,Std,Cumulative,Streak,L,C,S,S
                if (tokens.size >= 12) {
                    list.add(
                        ReelData(
                            sessionId = tokens[0],
                            reelIndex = tokens[1].toIntOrNull() ?: 0,
                            startTime = tokens[2],
                            dwellTime = if (tokens.size > 4) tokens[4] else "0",
                            timeOfDay = if (tokens.size > 5) tokens[5] else "-",
                            rollingMean = if (tokens.size > 8) tokens[8] else "0",
                            rollingStd = if (tokens.size > 11) tokens[11] else "0" // Display Streak here if new schema? logic needs update in data model really
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CSV_LOAD", "Error loading csv", e)
        }
        return list.reversed()
    }

    private fun exportCsv() {
        val file = File(filesDir, "insta_data.csv")
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share CSV"))
    }
}
