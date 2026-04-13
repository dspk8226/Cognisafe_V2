package com.guardian.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

/**
 * Main Settings Activity for Guardian Keyboard
 * Allows configuration of guardian number, keyword threshold, backend URL, and PIN
 */
class MainActivity : AppCompatActivity() {

    private lateinit var guardianNumberInput: EditText
    private lateinit var testTypingInput: EditText
    private lateinit var keywordThresholdInput: EditText
    private lateinit var backendUrlInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var saveButton: Button
    private lateinit var enableKeyboardButton: Button
    private lateinit var switchKeyboardButton: Button
    private lateinit var showKeyboardButton: Button
    private lateinit var testButton: Button
    private lateinit var statusText: TextView

    private val SMS_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        guardianNumberInput = findViewById(R.id.guardianNumberInput)
        testTypingInput = findViewById(R.id.testTypingInput)
        keywordThresholdInput = findViewById(R.id.keywordThresholdInput)
        backendUrlInput = findViewById(R.id.backendUrlInput)
        pinInput = findViewById(R.id.pinInput)
        saveButton = findViewById(R.id.saveButton)
        enableKeyboardButton = findViewById(R.id.enableKeyboardButton)
        switchKeyboardButton = findViewById(R.id.switchKeyboardButton)
        showKeyboardButton = findViewById(R.id.showKeyboardButton)
        testButton = findViewById(R.id.testButton)
        statusText = findViewById(R.id.statusText)

        // Load existing configuration
        loadConfiguration()

        // Request SMS permission if not granted
        requestSMSPermission()

        // Save button click handler
        saveButton.setOnClickListener {
            saveConfiguration()
        }

        // Enable keyboard button - opens Android keyboard settings
        enableKeyboardButton.setOnClickListener {
            openKeyboardSettings()
        }

        // Switch keyboard button - shows keyboard picker directly
        switchKeyboardButton.setOnClickListener {
            switchToGuardianKeyboard()
        }

        // Force-show the current keyboard (useful on emulators / hardware-keyboard setups)
        showKeyboardButton.setOnClickListener {
            try {
                testTypingInput.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(testTypingInput, InputMethodManager.SHOW_IMPLICIT)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to show keyboard: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Test button - manually trigger blocking activity
        testButton.setOnClickListener {
            testBlockingActivity()
        }
        
        // Check keyboard status on startup
        checkKeyboardStatus()
    }

    /**
     * Loads configuration from internal storage and populates UI
     */
    private fun loadConfiguration() {
        try {
            val configFile = File(filesDir, "config.json")
            if (configFile.exists()) {
                val jsonString = configFile.readText()
                val config = JSONObject(jsonString)
                
                guardianNumberInput.setText(config.optString("guardianNumber", ""))
                keywordThresholdInput.setText(config.optInt("keywordThreshold", 10).toString())
                backendUrlInput.setText(config.optString("backendBaseUrl", "http://10.0.2.2:8000/"))
                pinInput.setText(config.optString("pin", ""))
            } else {
                // Set defaults
                keywordThresholdInput.setText("10")
                backendUrlInput.setText("http://10.0.2.2:8000/")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading configuration", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves configuration to internal storage
     */
    private fun saveConfiguration() {
        try {
            val guardianNumber = guardianNumberInput.text.toString().trim()
            val keywordThreshold = keywordThresholdInput.text.toString().toIntOrNull() ?: 10
            val backendBaseUrl = backendUrlInput.text.toString().trim().ifEmpty { "http://10.0.2.2:8000/" }
            val pin = pinInput.text.toString().trim()

            // Validate threshold
            if (keywordThreshold < 1) {
                Toast.makeText(this, "Threshold must be at least 1", Toast.LENGTH_SHORT).show()
                return
            }

            // Save config.json
            val config = JSONObject().apply {
                put("guardianNumber", guardianNumber)
                put("keywordThreshold", keywordThreshold)
                put("backendBaseUrl", backendBaseUrl)
                put("pin", pin)
            }
            val configFile = File(filesDir, "config.json")
            configFile.writeText(config.toString())

            Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
            statusText.text = "Saved. Guardian: $guardianNumber, Keyword threshold: $keywordThreshold, Backend: $backendBaseUrl"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving configuration", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Requests SMS permission at runtime (required for Android 6.0+)
     */
    private fun requestSMSPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "SMS permission denied. Alerts will not be sent.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Check if Guardian Keyboard is enabled and set as default
     */
    private fun checkKeyboardStatus() {
        try {
            val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val enabledImes = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            
            val guardianImeId = "com.guardian.keyboard/.GuardianImeService"
            val isEnabled = enabledImes?.contains(guardianImeId) == true
            val isDefault = defaultIme == guardianImeId
            
            Log.d("GuardianKeyboard", "Keyboard Status:")
            Log.d("GuardianKeyboard", "  Enabled: $isEnabled")
            Log.d("GuardianKeyboard", "  Default: $isDefault")
            Log.d("GuardianKeyboard", "  Default IME: $defaultIme")
            Log.d("GuardianKeyboard", "  Enabled IMEs: $enabledImes")
            
            val statusMsg = if (isDefault) {
                "✓ Guardian Keyboard is active! You can now type in any app to test detection."
            } else if (isEnabled) {
                "⚠ Guardian Keyboard is enabled but Gboard is still default.\n" +
                "Tap 'Switch to Guardian Keyboard Now' button below to change it.\n" +
                "(You don't need to disable Gboard - just switch the default)"
            } else {
                "✗ Guardian Keyboard is not enabled.\n" +
                "Please enable it in Settings > System > Languages & Input > Virtual Keyboard"
            }
            
            // Show/hide switch button based on status
            switchKeyboardButton.visibility = if (isEnabled && !isDefault) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            statusText.text = statusMsg
        } catch (e: Exception) {
            Log.e("GuardianKeyboard", "Error checking keyboard status", e)
        }
    }

    /**
     * Test blocking activity manually
     */
    private fun testBlockingActivity() {
        try {
            val intent = Intent(this, BlockingActivity::class.java)
            startActivity(intent)
            Toast.makeText(this, "Blocking activity launched for testing", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Shows keyboard picker to switch to Guardian Keyboard
     */
    private fun switchToGuardianKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            Toast.makeText(
                this,
                "Select 'Guardian Keyboard' from the list",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("GuardianKeyboard", "Error showing keyboard picker", e)
            Toast.makeText(
                this,
                "Error: ${e.message}\nPlease use 'Open Keyboard Settings' button instead",
                Toast.LENGTH_LONG
            ).show()
            openKeyboardSettings()
        }
    }

    /**
     * Opens Android keyboard settings to enable Guardian Keyboard
     */
    private fun openKeyboardSettings() {
        try {
            // Try to open input method settings
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
            
            // Show instructions
            Toast.makeText(
                this,
                "In Settings:\n1. Find 'Guardian Keyboard'\n2. Tap it and enable\n3. Go back and set as default",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Fallback: try to open input method picker directly
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
                Toast.makeText(
                    this,
                    "Select 'Guardian Keyboard' from the list",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e2: Exception) {
                // Final fallback to general settings
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(
                        this,
                        "Please enable Guardian Keyboard in Settings > System > Languages & Input",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e3: Exception) {
                    Toast.makeText(
                        this,
                        "Please enable Guardian Keyboard in Settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

