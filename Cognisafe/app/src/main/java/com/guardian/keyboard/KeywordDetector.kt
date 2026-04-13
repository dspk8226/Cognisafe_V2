package com.guardian.keyboard

/**
 * Phrase-first weighted keyword detection with safe-phrase filtering.
 *
 * Requirements:
 * - Weighted keywords (examples):
 *   "kill myself" -> 10
 *   "hopeless" -> 6
 *   "sad" -> 3
 * - Safe phrase filtering:
 *   "kill time" should NOT trigger
 */
class KeywordDetector(
    private val rules: List<WeightedPhraseRule> = defaultRules(),
    private val safePhrases: List<String> = defaultSafePhrases()
) {
    data class Match(val phrase: String, val weight: Int)

    fun score(text: String): Pair<Int, List<Match>> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return 0 to emptyList()

        // If any safe phrase appears, we exclude its covered risky keyword occurrences by simply
        // removing the safe phrase spans from the text before scoring.
        val scrubbed = scrubSafePhrases(normalized)

        var total = 0
        val matches = ArrayList<Match>()

        for (rule in rules) {
            val isMatch = when (rule.matchMode) {
                MatchMode.CONTAINS -> scrubbed.contains(rule.phrase)
                MatchMode.CONTAINS_WORD_BOUNDARY -> containsWithWordBoundary(scrubbed, rule.phrase)
            }
            if (isMatch) {
                total += rule.weight
                matches.add(Match(rule.phrase, rule.weight))
            }
        }

        return total to matches
    }

    data class WeightedPhraseRule(
        val phrase: String,
        val weight: Int,
        val matchMode: MatchMode = MatchMode.CONTAINS_WORD_BOUNDARY
    )

    enum class MatchMode { CONTAINS, CONTAINS_WORD_BOUNDARY }

    private fun normalize(s: String): String {
        // Lowercase + collapse whitespace.
        return s.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scrubSafePhrases(normalizedText: String): String {
        var out = normalizedText
        for (safe in safePhrases) {
            out = out.replace(safe, " ")
        }
        return out.replace(Regex("\\s+"), " ").trim()
    }

    private fun containsWithWordBoundary(text: String, phrase: String): Boolean {
        // Word-boundary-ish match for phrases (handles start/end and whitespace/punct).
        val escaped = Regex.escape(phrase)
        val r = Regex("(^|[^a-z0-9])$escaped([^a-z0-9]|$)")
        return r.containsMatchIn(text)
    }

    companion object {
        fun defaultRules(): List<WeightedPhraseRule> = listOf(
            WeightedPhraseRule("kill myself", 10),
            WeightedPhraseRule("suicide", 10),
            WeightedPhraseRule("end it all", 9),
            WeightedPhraseRule("i want to die", 10),
            WeightedPhraseRule("hopeless", 6),
            WeightedPhraseRule("worthless", 6),
            WeightedPhraseRule("depressed", 6),
            WeightedPhraseRule("sad", 3),
            WeightedPhraseRule("lonely", 3),
            WeightedPhraseRule("tired of living", 8)
        )

        fun defaultSafePhrases(): List<String> = listOf(
            "kill time",
            "killing time"
        )
    }
}

