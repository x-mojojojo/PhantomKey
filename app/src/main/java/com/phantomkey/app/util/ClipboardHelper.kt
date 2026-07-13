package com.phantomkey.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies text to the clipboard and schedules an automatic clear.
 * Marks content as sensitive on supported API levels so the system
 * suppresses clipboard previews where possible.
 */
class ClipboardHelper(context: Context) {

    private val appContext = context.applicationContext
    private val clipboard =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var clearRunnable: Runnable? = null

    fun copySensitive(label: String, text: String, clearAfterSeconds: Int) {
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val extras = PersistableBundle()
            // Official flag on API 33+; still harmless to set the string key earlier.
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
            clip.description.extras = extras
        }
        clipboard.setPrimaryClip(clip)
        scheduleClear(text, clearAfterSeconds)
    }

    private fun scheduleClear(expected: String, seconds: Int) {
        clearRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            val current = runCatching {
                clipboard.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()
            }.getOrNull()
            if (current == expected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
        clearRunnable = runnable
        handler.postDelayed(runnable, seconds.coerceAtLeast(1) * 1000L)
    }

    fun cancelPendingClear() {
        clearRunnable?.let { handler.removeCallbacks(it) }
        clearRunnable = null
    }
}
