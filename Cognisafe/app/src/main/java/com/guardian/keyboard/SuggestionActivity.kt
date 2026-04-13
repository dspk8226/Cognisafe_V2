package com.guardian.keyboard

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

/**
 * Medium-risk visible intervention screen.
 * Auto-dismisses after a short delay, with optional manual close.
 */
class SuggestionActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggestion)

        val message = findViewById<TextView>(R.id.suggestionMessage)
        val close = findViewById<Button>(R.id.suggestionCloseButton)

        message.text = "You seem distressed. Take a slow breath and pause for a moment."
        close.setOnClickListener { finish() }

        handler.postDelayed(autoCloseRunnable, 5000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoCloseRunnable)
    }
}

