package com.guardian.keyboard

import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.io.File
import com.guardian.keyboard.network.BackendClient
import com.guardian.keyboard.network.PredictRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom Input Method Service (IME) that provides keyboard functionality
 * with real-time mental health risk detection:
 * - Instant rule-based keyword/phrase scoring (on-device)
 * - Async backend inference using pretrained MentalBERT (.pt weights loaded server-side)
 *
 * Privacy: no permanent storage; rolling buffer stays in-memory only.
 */
class GuardianImeService : InputMethodService() {

    companion object {
        private const val TAG = "GuardianIME"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val textBuffer = TextBufferManager(maxSentences = 30)
    private val keywordDetector = KeywordDetector()
    private var throttler = PipelineThrottler(cooldownMs = 20_000L)
    private var lastTriggeredContextSignature: Int? = null

    private var keywordThreshold = 10
    private var guardianNumber: String = ""
    private var backendBaseUrl: String = "http://10.0.2.2:8000/"
    
    // View for the keyboard input
    private var inputView: View? = null
    private var keyboardView: GuardianKeyboardView? = null
    private val isDebugBuild: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== GuardianImeService.onCreate() ==========")
        loadConfiguration()
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "========== onCreateInputView called - keyboard view being created ==========")
        // Load configuration when keyboard starts
        loadConfiguration()
        Log.d(TAG, "Loaded config - keywordThreshold: $keywordThreshold, guardian: $guardianNumber, backend: $backendBaseUrl")
        
        // Create soft-touch keyboard view and wire to guardian logic
        inputView = layoutInflater.inflate(R.layout.input_view, null)
        keyboardView = inputView!!.findViewById(R.id.guardian_keyboard)
        keyboardView?.onCommitText = { text ->
            commitTextFromKeyboard(text)
        }
        keyboardView?.onSpecialKey = { key ->
            when (key) {
                "backspace" -> deleteBackward()
                "enter" -> {
                    textBuffer.onMessageBoundary()
                    sendEnterAction()
                    maybeTriggerPipeline(reason = "enter")
                }
            }
            updateImeDrivenKeyStates()
        }
        Log.d(TAG, "Input view (soft keyboard) created successfully")
        updateImeDrivenKeyStates()
        return inputView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "========== onStartInputView - Keyboard started ==========")
        // Reload configuration each time keyboard becomes active
        loadConfiguration()
        // Check for reset flag from BlockingActivity
        checkResetFlag()
        Log.d(TAG, "keywordThreshold: $keywordThreshold, guardian: $guardianNumber, backend: $backendBaseUrl")
        
        // Set up text monitoring by wrapping the current input connection
        setupTextMonitoring()

        // Update enter label + auto-caps based on the focused editor
        updateImeDrivenKeyStates(info)
    }
    
    /**
     * Sets up text monitoring
     * Text input is captured through onKeyDown method when keys are pressed
     */
    private fun setupTextMonitoring() {
        // Text monitoring happens through onKeyDown method
        // All keyboard input goes through onKeyDown when Guardian Keyboard is active
    }

    /**
     * Checks for reset flag file and resets score if present
     */
    private fun checkResetFlag() {
        try {
            val resetFile = File(filesDir, "reset_score.flag")
            if (resetFile.exists()) {
                textBuffer.clearAll()
                resetFile.delete() // Remove flag after processing
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads configuration from internal storage.
     */
    private fun loadConfiguration() {
        try {
            val configFile = File(filesDir, "config.json")
            if (configFile.exists()) {
                val jsonString = configFile.readText()
                val config = org.json.JSONObject(jsonString)
                keywordThreshold = config.optInt("keywordThreshold", 10)
                guardianNumber = config.optString("guardianNumber", "")
                backendBaseUrl = config.optString("backendBaseUrl", backendBaseUrl)
            } else {
                // Default values
                keywordThreshold = 10
                guardianNumber = ""
            }
            // Avoid over-triggering from accidental tiny thresholds.
            keywordThreshold = keywordThreshold.coerceAtLeast(3)
        } catch (e: Exception) {
            e.printStackTrace()
            keywordThreshold = 10
            guardianNumber = ""
        }
    }

    /**
     * Override onFinishInput to process any remaining words when input session ends
     */
    override fun onFinishInput() {
        super.onFinishInput()
        // No persistence.
    }

    /**
     * Handles key down events to capture typed characters
     * This method is called when keys are pressed on the keyboard
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isDebugBuild) {
            Log.d(TAG, "onKeyDown called: keyCode=$keyCode, unicodeChar=${event?.unicodeChar}")
        }
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            Log.w(TAG, "No InputConnection available!")
            return super.onKeyDown(keyCode, event)
        }
        
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                inputConnection.commitText(" ", 1)
                textBuffer.onTextCommitted(" ")
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                textBuffer.onMessageBoundary()
                maybeTriggerPipeline(reason = "enter_hw")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DEL -> {
                textBuffer.onBackspace()
                return super.onKeyDown(keyCode, event)
            }
            else -> {
                // Capture printable characters
                val unicodeChar = event?.unicodeChar ?: 0
                val char = unicodeChar.toChar()
                if (unicodeChar != 0) {
                    textBuffer.onTextCommitted(char.toString())
                    if (char == '.' || char == '!' || char == '?' || char == '\n') {
                        maybeTriggerPipeline(reason = "punct_hw")
                    }
                }
                return super.onKeyDown(keyCode, event)
            }
        }
    }

    /**
     * Commits text from the soft keyboard and runs guardian detection.
     */
    private fun commitTextFromKeyboard(text: String) {
        if (text.isEmpty()) return
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        onTextCommitted(text)
        updateImeDrivenKeyStates()
    }

    /**
     * Deletes one character before the cursor (backspace).
     */
    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        textBuffer.onBackspace()
        ic.deleteSurroundingText(1, 0)
    }

    /**
     * Sends enter/IME action (e.g. done, next, search), falling back to KEYCODE_ENTER.
     */
    private fun sendEnterAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED

        val performed = when (action) {
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND -> ic.performEditorAction(action)
            else -> false
        }

        if (!performed) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        updateImeDrivenKeyStates()
    }

    private fun updateImeDrivenKeyStates(info: EditorInfo? = currentInputEditorInfo) {
        // Auto-capitalization: align shift state with cursor caps mode.
        val ic = currentInputConnection
        val capsMode = ic?.getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) ?: 0
        keyboardView?.setAutoCaps(capsMode != 0)

        // Enter key label based on imeOptions action.
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val label = when (action) {
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            else -> "↵"
        }
        keyboardView?.setEnterLabel(label)
    }

    /**
     * Helper method to handle text committed directly via InputConnection
     * Can be called from keyboard UI when text is committed
     */
    fun onTextCommitted(text: CharSequence) {
        if (isDebugBuild) Log.d(TAG, "Text committed len=${text.length}")
        textBuffer.onTextCommitted(text.toString())
    }

    private fun maybeTriggerPipeline(reason: String) {
        // Trigger only on strong boundaries (message or sentence end).
        if (!(reason.contains("enter") || reason.contains("punct"))) return

        val recent = textBuffer.getRecentSentences(25)
        val context = ContextBuilder.buildContext(recent)
        val (kwScore, matches) = keywordDetector.score(context)
        val engine = RiskDecisionEngine(keywordThreshold = keywordThreshold)

        // Safety-first: run override logic before threshold gating.
        val overrideLevel = engine.computeRiskLevel(
            context = context,
            keywordMatches = matches,
            keywordScore = kwScore,
            modelConfidence = 0.0
        )
        if (overrideLevel == RiskDecisionEngine.RiskLevel.CRITICAL) {
            Log.w(TAG, "Critical override intervention triggered before backend call")
            showWarningOverlay()
            if (guardianNumber.isNotEmpty()) sendSMSAlert()
            textBuffer.clearAll()
            return
        }
        if (overrideLevel == RiskDecisionEngine.RiskLevel.HIGH && kwScore >= 12) {
            Log.w(TAG, "High keyword override intervention triggered before backend call")
            showWarningOverlay()
            textBuffer.clearAll()
            return
        }

        if (kwScore < keywordThreshold) return
        if (context.length < 30) return

        val signature = context.hashCode()
        if (lastTriggeredContextSignature == signature) return
        if (!throttler.shouldTrigger()) return

        throttler.markTriggered()
        lastTriggeredContextSignature = signature
        Log.w(TAG, "Keyword threshold reached ($kwScore >= $keywordThreshold) reason=$reason matches=$matches")

        serviceScope.launch {
            try {
                val api = BackendClient.create(backendBaseUrl)
                val resp = withContext(Dispatchers.IO) {
                    api.predict(PredictRequest(text = context))
                }

                val decision = engine.decide(
                    context = context,
                    keywordMatches = matches,
                    keywordScore = kwScore,
                    modelConfidence = (resp.probabilities.maxOrNull() ?: 0.0)
                )
                Log.i(
                    TAG,
                    "Decision: level=${decision.level}, risk=${"%.3f".format(decision.riskScore)}, kw=$kwScore"
                )

                when (decision.level) {
                    RiskDecisionEngine.RiskLevel.LOW -> Unit
                    RiskDecisionEngine.RiskLevel.MEDIUM -> showSuggestionPopup()
                    RiskDecisionEngine.RiskLevel.HIGH -> showWarningOverlay()
                    RiskDecisionEngine.RiskLevel.CRITICAL -> {
                        showWarningOverlay()
                        if (guardianNumber.isNotEmpty()) sendSMSAlert()
                    }
                }

                // Reduce repeated alerts from stale risky context.
                if (decision.level != RiskDecisionEngine.RiskLevel.LOW) {
                    textBuffer.clearAll()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend inference failed", e)
            }
        }
    }

    private fun showSuggestionPopup() {
        keyboardView?.showSuggestionMessage("You seem stressed. Try taking 3 slow breaths.")
        try {
            val intent = Intent(this, SuggestionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SuggestionActivity", e)
        }
    }

    private fun showWarningOverlay() {
        try {
            val intent = Intent(this, BlockingActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BlockingActivity", e)
        }
    }

    /**
     * Triggers safety alert: shows blocking activity and sends SMS
     */
    // BlockingActivity overlay is triggered by decision engine.

    /**
     * Sends SMS alert to guardian number
     */
    private fun sendSMSAlert() {
        try {
            // Check SMS permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "SMS permission not granted!")
                return
            }

            val smsManager: SmsManager = SmsManager.getDefault()
            val message = "CogniSafe alert: high-risk distress detected via keyboard."
            
            Log.d(TAG, "Sending SMS to: $guardianNumber")
            Log.d(TAG, "Message: $message")
            
            smsManager.sendTextMessage(guardianNumber, null, message, null, null)
            Log.d(TAG, "SMS sent successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending SMS", e)
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            e.printStackTrace()
        }
    }

    /**
     * Public method to reset buffer (can be called from BlockingActivity via reset flag).
     */
    fun resetScore() {
        textBuffer.clearAll()
    }

    /**
     * Public method to get current backspace count (debugging)
     */
    fun getCurrentScore(): Int {
        return textBuffer.getBackspaceCount()
    }
}

