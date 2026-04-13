package com.guardian.keyboard

object ContextBuilder {
    /**
     * Format MUST start exactly with:
     * "User recent thoughts: "
     */
    fun buildContext(recentSentences: List<String>): String {
        val body = recentSentences
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n")

        return "User recent thoughts: $body"
    }
}

