package com.guardian.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.File
import org.json.JSONObject

/**
 * Full-screen blocking overlay activity that appears when unsafe content
 * threshold is exceeded. Requires PIN entry to dismiss (optional).
 */
class BlockingActivity : Activity() {

    private lateinit var messageText: TextView
    private lateinit var pinInput: EditText
    private lateinit var dismissButton: Button
    private var guardianPin: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocking)
        
        // Load PIN from config
        loadPinFromConfig()
        
        // Initialize views
        messageText = findViewById(R.id.blockingMessage)
        pinInput = findViewById(R.id.pinInput)
        dismissButton = findViewById(R.id.dismissButton)
        
        messageText.text = "You seem distressed. Take a moment."
        
        // Handle dismiss button click
        dismissButton.setOnClickListener {
            val enteredPin = pinInput.text.toString()
            
            if (guardianPin.isEmpty() || enteredPin == guardianPin) {
                // PIN matches or no PIN set - dismiss blocking overlay
                finish()
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                pinInput.text.clear()
            }
        }
        
        // Prevent back button from dismissing
        // This can be customized based on requirements
    }

    override fun onBackPressed() {
        // Optionally disable back button to prevent bypassing
        // Uncomment below to allow back button:
        // super.onBackPressed()
    }

    /**
     * Loads PIN from configuration file
     */
    private fun loadPinFromConfig() {
        try {
            val configFile = File(filesDir, "config.json")
            if (configFile.exists()) {
                val jsonString = configFile.readText()
                val config = JSONObject(jsonString)
                guardianPin = config.optString("pin", "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            guardianPin = ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Reset score in IME service when blocking is dismissed
        // Note: This requires a way to communicate with the IME service
        // For simplicity, we'll reset via shared preferences or file
        resetIMEScore()
    }

    /**
     * Resets the IME service score by writing to a file
     */
    private fun resetIMEScore() {
        try {
            val resetFile = File(filesDir, "reset_score.flag")
            resetFile.writeText("reset")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

