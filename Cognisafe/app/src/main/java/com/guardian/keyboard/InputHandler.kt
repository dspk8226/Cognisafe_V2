package com.guardian.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Encapsulates low-level editor interactions for IME input operations.
 * Keeps commit/delete/enter behavior consistent and easy to test.
 */
class InputHandler {

    fun commitText(ic: InputConnection?, text: String) {
        if (ic == null || text.isEmpty()) return
        ic.commitText(text, 1)
    }

    fun deleteBackward(ic: InputConnection?) {
        if (ic == null) return
        ic.deleteSurroundingText(1, 0)
    }

    fun sendEnter(ic: InputConnection?, info: EditorInfo?): Boolean {
        if (ic == null) return false
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
        return true
    }
}

