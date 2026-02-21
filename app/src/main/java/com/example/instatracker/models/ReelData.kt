package com.example.instatracker.models

data class ReelData(
    val sessionId: String,
    val reelIndex: Int,
    val startTime: String,
    val dwellTime: String,
    val timeOfDay: String,
    val rollingMean: String,
    val rollingStd: String
)
