package com.guardian.keyboard

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Owns keyboard UI wiring and state sync from IME/editor context.
 */
class KeyboardViewManager(
    private val inflater: LayoutInflater
) {
    private var inputView: View? = null
    private var keyboardView: GuardianKeyboardView? = null

    fun createInputView(
        onCommitText: (String) -> Unit,
        onSpecialKey: (String) -> Unit
    ): View {
        val view = inflater.inflate(R.layout.input_view, null)
        val kv = view.findViewById<GuardianKeyboardView>(R.id.guardian_keyboard)
        kv.onCommitText = onCommitText
        kv.onSpecialKey = onSpecialKey

        inputView = view
        keyboardView = kv
        return view
    }

    fun updateImeDrivenKeyStates(info: EditorInfo?, ic: InputConnection?) {
        val capsMode = ic?.getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) ?: 0
        keyboardView?.setAutoCaps(capsMode != 0)

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

    fun showSuggestionMessage(message: String) {
        keyboardView?.showSuggestionMessage(message)
    }
}

