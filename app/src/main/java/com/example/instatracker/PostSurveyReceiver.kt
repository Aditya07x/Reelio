package com.example.instatracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PostSurveyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionNum = intent.getIntExtra("session_num", -1)
        if (sessionNum < 0) return

        val prefs = context.getSharedPreferences("InstaTrackerPrefs", Context.MODE_PRIVATE)
        val pendingSession = prefs.getInt("pending_survey_session_num", -1)
        val completedSession = prefs.getInt("survey_completed_for_session", -1)
        val surveyOpen = prefs.getBoolean("survey_activity_open", false)

        if (pendingSession != sessionNum) {
            Log.i("ReelioDiag", "PostSurveyReceiver skip: pending=$pendingSession session=$sessionNum")
            return
        }
        if (completedSession == sessionNum) {
            Log.i("ReelioDiag", "PostSurveyReceiver skip: already completed session=$sessionNum")
            return
        }
        if (surveyOpen) {
            Log.i("ReelioDiag", "PostSurveyReceiver skip: survey activity already open")
            return
        }

        InstaAccessibilityService.showPostSurveyNotification(context, sessionNum)
    }
}
