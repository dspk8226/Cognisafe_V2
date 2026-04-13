package com.guardian.keyboard

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * A Gboard-like soft keyboard view:
 * - QWERTY layout with row indents
 * - Shift (tap, double-tap for caps-lock)
 * - 123 <-> ABC symbols toggle
 * - Key-preview popup
 * - Backspace long-press repeat
 * - Haptic feedback
 */
class GuardianKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val ACTION_BACKSPACE = "backspace"
        private const val ACTION_ENTER = "enter"
        private const val ACTION_SHIFT = "shift"
        private const val ACTION_MODE_TOGGLE = "mode_toggle"

        private val LETTER_ROW1 = listOf("q","w","e","r","t","y","u","i","o","p")
        private val LETTER_ROW2 = listOf("a","s","d","f","g","h","j","k","l")
        private val LETTER_ROW3 = listOf("z","x","c","v","b","n","m")

        private val SYMBOL_ROW1 = listOf("1","2","3","4","5","6","7","8","9","0")
        private val SYMBOL_ROW2 = listOf("@","#","$","%","&","-","+","(",")")
        private val SYMBOL_ROW3 = listOf("*","\"","'",";",":","!","?")
    }

    private enum class Mode { LETTERS, SYMBOLS }
    private enum class SymbolsPage { PAGE_1, PAGE_2 }
    private enum class ShiftState { OFF, ONCE, LOCKED }

    private var mode: Mode = Mode.LETTERS
    private var symbolsPage: SymbolsPage = SymbolsPage.PAGE_1
    private var shiftState: ShiftState = ShiftState.OFF

    /** Commit text to the editor (letters/symbols/space/punctuation). */
    var onCommitText: ((String) -> Unit)? = null

    /** Handle special keys (backspace, enter). */
    var onSpecialKey: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null
    private var backspaceRepeating = false

    private var keyPreviewPopup: PopupWindow? = null
    private var keyPreviewText: TextView? = null
    private val tmpRect = Rect()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val keyHeightPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.keyboard_key_height).coerceAtLeast(40)

    private val keyHorizontalGapPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.keyboard_horizontal_gap)

    private val keyVerticalGapPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.keyboard_vertical_gap)

    private val keyTextSizePx: Float
        get() = resources.getDimension(R.dimen.keyboard_key_text_size)

    private val keyPaddingVPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.keyboard_key_padding_v)

    private var enterKeyView: TextView? = null
    private var shiftKeyView: TextView? = null
    private var modeKeyView: TextView? = null
    private var spaceKeyView: TextView? = null
    private var suggestionBannerView: TextView? = null
    private var hideSuggestionRunnable: Runnable? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_bg))
        setPadding(keyHorizontalGapPx, keyVerticalGapPx, keyHorizontalGapPx, keyVerticalGapPx)
        isHapticFeedbackEnabled = true
        rebuild()
    }

    /** IME can set the enter key label (Done/Go/Search/Next). */
    fun setEnterLabel(label: String) {
        enterKeyView?.text = label
    }

    /**
     * IME can hint whether letters should be capitalized (e.g. start of sentence).
     * If caps-lock is enabled, this has no effect.
     */
    fun setAutoCaps(isCaps: Boolean) {
        if (mode != Mode.LETTERS) return
        if (shiftState == ShiftState.LOCKED) return
        shiftState = if (isCaps) ShiftState.ONCE else ShiftState.OFF
        updateShiftKeyVisual()
        updateLetterCase()
    }

    private fun rebuild() {
        removeAllViews()
        enterKeyView = null
        shiftKeyView = null
        modeKeyView = null
        spaceKeyView = null
        suggestionBannerView = null

        addSuggestionBanner()

        when (mode) {
            Mode.LETTERS -> {
                addKeyRow(LETTER_ROW1, indentWeight = 0f)
                addKeyRow(LETTER_ROW2, indentWeight = 0.55f)
                addLetterRow3()
                addBottomRowLetters()
                updateLetterCase()
                updateShiftKeyVisual()
                modeKeyView?.text = "123"
            }
            Mode.SYMBOLS -> {
                val page1 = symbolsPage == SymbolsPage.PAGE_1
                addKeyRow(
                    if (page1) SYMBOL_ROW1 else listOf("[", "]", "{", "}", "<", ">", "^", "~", "`", "|"),
                    indentWeight = 0f,
                    isSymbols = true
                )
                addKeyRow(
                    if (page1) SYMBOL_ROW2 else listOf("\\", "_", "=", "+", "€", "£", "¥", "•", "°"),
                    indentWeight = 0.35f,
                    isSymbols = true
                )
                addSymbolsRow3(page1)
                addBottomRowSymbols()
                shiftState = ShiftState.OFF
                updateShiftKeyVisual()
                modeKeyView?.text = "ABC"
            }
        }
    }

    /**
     * Shows a transient message directly inside the keyboard UI.
     * This is more reliable than Toast for IME apps.
     */
    fun showSuggestionMessage(message: String, durationMs: Long = 3500L) {
        val banner = suggestionBannerView ?: return
        banner.text = message
        banner.visibility = VISIBLE

        hideSuggestionRunnable?.let { mainHandler.removeCallbacks(it) }
        hideSuggestionRunnable = Runnable {
            banner.visibility = GONE
        }
        mainHandler.postDelayed(hideSuggestionRunnable!!, durationMs)
    }

    private fun addSuggestionBanner() {
        val banner = TextView(context).apply {
            visibility = GONE
            gravity = Gravity.CENTER
            text = ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_alert_bg))
            setPadding(
                keyHorizontalGapPx * 2,
                keyPaddingVPx,
                keyHorizontalGapPx * 2,
                keyPaddingVPx
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = keyVerticalGapPx
            }
        }
        suggestionBannerView = banner
        addView(banner)
    }

    private fun addKeyRow(keys: List<String>, indentWeight: Float, isSymbols: Boolean = false) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = keyVerticalGapPx
            }
        }
        if (indentWeight > 0f) row.addView(spaceStub(), keyLayoutParams(indentWeight))
        keys.forEach { label ->
            row.addView(makeTextKey(label, isSpecial = false, isSymbols = isSymbols), keyLayoutParams(1f))
        }
        if (indentWeight > 0f) row.addView(spaceStub(), keyLayoutParams(indentWeight))
        addView(row)
    }

    private fun addLetterRow3() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = keyVerticalGapPx
            }
        }

        shiftKeyView = makeSpecialKey("⇧", ACTION_SHIFT).also { row.addView(it, keyLayoutParams(1.35f)) }
        LETTER_ROW3.forEach { label ->
            row.addView(makeTextKey(label, isSpecial = false), keyLayoutParams(1f))
        }
        row.addView(makeSpecialKey("⌫", ACTION_BACKSPACE, isBackspace = true), keyLayoutParams(1.35f))

        addView(row)
    }

    private fun addSymbolsRow3(page1: Boolean) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = keyVerticalGapPx
            }
        }

        // In symbols mode, "shift" key toggles symbols pages.
        shiftKeyView = makeSpecialKey(if (page1) "1/2" else "2/2", ACTION_SHIFT).also { row.addView(it, keyLayoutParams(1.35f)) }
        (if (page1) SYMBOL_ROW3 else listOf("/", "\"", "'", ";", ":", "!", "?")).forEach { label ->
            row.addView(makeTextKey(label, isSpecial = false, isSymbols = true), keyLayoutParams(1f))
        }
        row.addView(makeSpecialKey("⌫", ACTION_BACKSPACE, isBackspace = true), keyLayoutParams(1.35f))

        addView(row)
    }

    private fun addBottomRowLetters() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = keyVerticalGapPx
            }
        }

        modeKeyView = makeSpecialKey("123", ACTION_MODE_TOGGLE).also { row.addView(it, keyLayoutParams(1.4f)) }
        row.addView(makeTextKey(",", isSpecial = true, isSymbols = true), keyLayoutParams(0.9f))
        spaceKeyView = makeSpaceKey().also { row.addView(it, keyLayoutParams(3.4f)) }
        row.addView(makeTextKey(".", isSpecial = true, isSymbols = true), keyLayoutParams(0.9f))
        enterKeyView = makeSpecialKey("↵", ACTION_ENTER).also { row.addView(it, keyLayoutParams(1.4f)) }

        addView(row)
    }

    private fun addBottomRowSymbols() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = keyVerticalGapPx
            }
        }

        modeKeyView = makeSpecialKey("ABC", ACTION_MODE_TOGGLE).also { row.addView(it, keyLayoutParams(1.4f)) }
        row.addView(makeTextKey(",", isSpecial = true, isSymbols = true), keyLayoutParams(0.9f))
        spaceKeyView = makeSpaceKey().also { row.addView(it, keyLayoutParams(3.4f)) }
        row.addView(makeTextKey(".", isSpecial = true, isSymbols = true), keyLayoutParams(0.9f))
        enterKeyView = makeSpecialKey("↵", ACTION_ENTER).also { row.addView(it, keyLayoutParams(1.4f)) }

        addView(row)
    }

    private fun makeSpaceKey(): TextView {
        return makeTextKey("space", isSpecial = true, isSymbols = true).apply {
            tag = " "
            setTextColor(ContextCompat.getColor(context, R.color.keyboard_key_text_secondary))
        }
    }

    private fun makeTextKey(label: String, isSpecial: Boolean, isSymbols: Boolean = false): TextView {
        val tv = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_PX, keyTextSizePx)
            setTextColor(ContextCompat.getColor(context, R.color.keyboard_key_text))
            gravity = Gravity.CENTER
            setBackgroundResource(if (isSpecial) R.drawable.key_special_background else R.drawable.key_background)
            setPadding(keyHorizontalGapPx, keyPaddingVPx, keyHorizontalGapPx, keyPaddingVPx)
            minHeight = keyHeightPx
            tag = label
        }

        tv.setOnTouchListener { _, event ->
            handleKeyTouch(tv, event, isSpecial = isSpecial, isBackspace = false, isSymbols = isSymbols)
        }
        return tv
    }

    private fun makeSpecialKey(label: String, actionTag: String, isBackspace: Boolean = false): TextView {
        val tv = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_PX, keyTextSizePx * 0.9f)
            setTextColor(ContextCompat.getColor(context, R.color.keyboard_key_text))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.key_special_background)
            setPadding(keyHorizontalGapPx, keyPaddingVPx, keyHorizontalGapPx, keyPaddingVPx)
            minHeight = keyHeightPx
            tag = actionTag
        }

        tv.setOnTouchListener { _, event ->
            handleKeyTouch(tv, event, isSpecial = true, isBackspace = isBackspace, isSymbols = true)
        }
        return tv
    }

    private fun spaceStub(): TextView {
        return TextView(context).apply {
            text = ""
            minHeight = keyHeightPx
            isClickable = false
            isFocusable = false
            background = null
        }
    }

    private fun keyLayoutParams(weight: Float): LayoutParams {
        return LayoutParams(0, LayoutParams.WRAP_CONTENT, weight).apply {
            marginStart = keyHorizontalGapPx / 2
            marginEnd = keyHorizontalGapPx / 2
            topMargin = keyVerticalGapPx / 2
        }
    }

    private fun handleKeyTouch(
        keyView: TextView,
        event: MotionEvent,
        isSpecial: Boolean,
        isBackspace: Boolean,
        isSymbols: Boolean
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                keyView.isPressed = true
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (!isSpecial) showKeyPreview(keyView)
                if (isBackspace) startBackspaceRepeat()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val inside = isPointInsideView(event.rawX, event.rawY, keyView, slop = touchSlop)
                keyView.isPressed = inside
                if (!inside) {
                    dismissKeyPreview()
                    if (isBackspace) stopBackspaceRepeat()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val inside = keyView.isPressed
                keyView.isPressed = false
                dismissKeyPreview()
                if (isBackspace) stopBackspaceRepeat()
                if (inside) dispatchKey(keyView.tag, isSymbols = isSymbols)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                keyView.isPressed = false
                dismissKeyPreview()
                if (isBackspace) stopBackspaceRepeat()
                return true
            }
        }
        return false
    }

    private fun dispatchKey(tag: Any?, isSymbols: Boolean) {
        val s = tag as? String ?: return
        when (s) {
            ACTION_BACKSPACE -> onSpecialKey?.invoke(ACTION_BACKSPACE)
            ACTION_ENTER -> onSpecialKey?.invoke(ACTION_ENTER)
            ACTION_SHIFT -> handleShiftPressed()
            ACTION_MODE_TOGGLE -> toggleMode()
            " " -> onCommitText?.invoke(" ")
            else -> {
                val out = if (mode == Mode.LETTERS && s.length == 1 && s[0].isLetter()) applyShiftToLetter(s) else s
                onCommitText?.invoke(out)
                if (mode == Mode.LETTERS && shiftState == ShiftState.ONCE) {
                    shiftState = ShiftState.OFF
                    updateShiftKeyVisual()
                    updateLetterCase()
                }
            }
        }
    }

    private fun applyShiftToLetter(letter: String): String {
        val c = letter[0]
        return when (shiftState) {
            ShiftState.OFF -> c.lowercaseChar().toString()
            ShiftState.ONCE, ShiftState.LOCKED -> c.uppercaseChar().toString()
        }
    }

    private fun handleShiftPressed() {
        if (mode == Mode.SYMBOLS) {
            symbolsPage = if (symbolsPage == SymbolsPage.PAGE_1) SymbolsPage.PAGE_2 else SymbolsPage.PAGE_1
            rebuild()
            return
        }

        val now = System.currentTimeMillis()
        val lastTap = (shiftKeyView?.getTag(R.id.keyboard_shift_last_tap) as? Long) ?: 0L
        val doubleTapWindow = ViewConfiguration.getDoubleTapTimeout().toLong()

        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (mode == Mode.LETTERS && now - lastTap <= doubleTapWindow) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }

        shiftKeyView?.setTag(R.id.keyboard_shift_last_tap, now)
        updateShiftKeyVisual()
        updateLetterCase()
    }

    private fun toggleMode() {
        mode = if (mode == Mode.LETTERS) Mode.SYMBOLS else Mode.LETTERS
        shiftState = ShiftState.OFF
        if (mode == Mode.SYMBOLS) symbolsPage = SymbolsPage.PAGE_1
        rebuild()
    }

    private fun updateLetterCase() {
        if (mode != Mode.LETTERS) return
        forEachKeyView { tv ->
            val t = tv.tag as? String ?: return@forEachKeyView
            if (t.length == 1 && t[0].isLetter()) {
                tv.text = applyShiftToLetter(t)
            }
        }
    }

    private fun updateShiftKeyVisual() {
        val tv = shiftKeyView ?: return
        tv.text = when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.ONCE -> "⇧"
            ShiftState.LOCKED -> "⇪"
        }
    }

    private fun forEachKeyView(block: (TextView) -> Unit) {
        for (i in 0 until childCount) {
            val row = getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val v = row.getChildAt(j) as? TextView ?: continue
                block(v)
            }
        }
    }

    private fun showKeyPreview(keyView: TextView) {
        val label = keyView.text?.toString()?.trim().orEmpty()
        if (label.isEmpty()) return

        if (keyPreviewPopup == null) {
            keyPreviewText = TextView(context).apply {
                setBackgroundResource(R.drawable.key_preview_background)
                setTextColor(ContextCompat.getColor(context, R.color.keyboard_key_text))
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.keyboard_key_preview_text_size)
                )
                gravity = Gravity.CENTER
                val p = resources.getDimensionPixelSize(R.dimen.keyboard_key_preview_padding)
                setPadding(p, p, p, p)
            }
            keyPreviewPopup = PopupWindow(
                keyPreviewText,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            ).apply {
                isClippingEnabled = false
            }
        }

        keyPreviewText?.text = label
        keyView.getGlobalVisibleRect(tmpRect)

        val x = tmpRect.centerX()
        val y = tmpRect.top
        try {
            keyPreviewPopup?.showAtLocation(this, Gravity.NO_GRAVITY, x, y - keyHeightPx * 2)
        } catch (_: Exception) {
            // Safe no-op: popup placement can fail on some devices.
        }
    }

    private fun dismissKeyPreview() {
        try {
            keyPreviewPopup?.dismiss()
        } catch (_: Exception) {
        }
    }

    private fun startBackspaceRepeat() {
        if (backspaceRepeating) return
        backspaceRepeating = true

        val initialDelay = 380L
        val repeatDelay = 55L

        backspaceRepeatRunnable = object : Runnable {
            override fun run() {
                if (!backspaceRepeating) return
                onSpecialKey?.invoke(ACTION_BACKSPACE)
                mainHandler.postDelayed(this, repeatDelay)
            }
        }

        // First delete immediately.
        onSpecialKey?.invoke(ACTION_BACKSPACE)
        mainHandler.postDelayed(backspaceRepeatRunnable!!, initialDelay)
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeating = false
        backspaceRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
    }

    private fun isPointInsideView(rawX: Float, rawY: Float, view: TextView, slop: Int): Boolean {
        view.getGlobalVisibleRect(tmpRect)
        tmpRect.inset(-slop, -slop)
        return tmpRect.contains(rawX.toInt(), rawY.toInt())
    }
}
