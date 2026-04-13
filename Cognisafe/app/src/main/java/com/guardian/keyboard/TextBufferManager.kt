package com.guardian.keyboard

import java.util.ArrayDeque

/**
 * Keeps recent user text in-memory only (privacy).
 *
 * We treat "Enter" as a message boundary.
 * We also split on sentence-ending punctuation to keep a rolling sentence buffer.
 */
class TextBufferManager(
    private val maxSentences: Int = 30
) {
    private val sentences: ArrayDeque<String> = ArrayDeque()
    private val current: StringBuilder = StringBuilder()

    private var backspaceCount: Int = 0

    fun onTextCommitted(text: String) {
        if (text.isEmpty()) return
        for (ch in text) {
            when {
                ch == '\n' -> onMessageBoundary()
                ch == '.' || ch == '!' || ch == '?' -> {
                    current.append(ch)
                    flushCurrentAsSentence()
                }
                else -> current.append(ch)
            }
        }
    }

    fun onBackspace() {
        backspaceCount++
        if (current.isNotEmpty()) {
            current.deleteCharAt(current.length - 1)
        }
    }

    fun onMessageBoundary() {
        flushCurrentAsSentence()
    }

    fun getRecentSentences(n: Int): List<String> {
        if (n <= 0) return emptyList()
        val out = ArrayList<String>(minOf(n, sentences.size))
        val it = sentences.descendingIterator()
        while (it.hasNext() && out.size < n) {
            out.add(it.next())
        }
        out.reverse()
        return out
    }

    fun getBackspaceCount(): Int = backspaceCount

    fun clearAll() {
        sentences.clear()
        current.clear()
        backspaceCount = 0
    }

    private fun flushCurrentAsSentence() {
        val s = current.toString().trim()
        current.clear()
        if (s.isEmpty()) return

        sentences.addLast(s)
        while (sentences.size > maxSentences) {
            sentences.removeFirst()
        }
    }
}

