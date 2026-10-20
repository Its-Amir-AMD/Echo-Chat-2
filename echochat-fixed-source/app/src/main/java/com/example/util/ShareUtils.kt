package com.example.util

import android.content.Context
import android.content.Intent

/** Opens the Android Sharesheet instead of silently copying text to the clipboard. */
fun shareText(context: Context, text: String, title: String) {
    val cleanText = text.trim()
    if (cleanText.isEmpty()) return

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, cleanText)
    }
    context.startActivity(Intent.createChooser(sendIntent, title))
}
