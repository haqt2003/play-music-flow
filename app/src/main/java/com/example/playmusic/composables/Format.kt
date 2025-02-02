package com.example.playmusic.composables

import android.annotation.SuppressLint

@SuppressLint("DefaultLocale")
fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 60000
    val seconds = (durationMs % 60000) / 1000
    return String.format("%02d:%02d", minutes, seconds)
}
