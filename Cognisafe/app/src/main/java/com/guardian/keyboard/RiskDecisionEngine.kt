package com.guardian.keyboard

import android.util.Log

class RiskDecisionEngine(
    private val keywordThreshold: Int
) {
    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    data class Decision(
        val riskScore: Double,
        val level: RiskLevel
    )

    private val criticalPhrases = listOf(
        "kill myself",
        "end my life",
        "want to die",
        "suicide"
    )

    private val highSeverityPhrases = listOf(
        "hopeless",
        "end it all",
        "tired of living"
    )

    fun computeRiskLevel(
        context: String,
        keywordMatches: List<KeywordDetector.Match>,
        keywordScore: Int,
        modelConfidence: Double
    ): RiskLevel {
        val normalizedContext = context.lowercase()
        val normalizedMatches = keywordMatches.map { it.phrase.lowercase() }

        val criticalMatch = normalizedMatches.any { match ->
            criticalPhrases.any { cp -> match.contains(cp) || cp.contains(match) }
        }
        if (criticalMatch || containsCriticalPhrase(normalizedContext)) {
            Log.i("DecisionEngine", "CRITICAL override triggered")
            return RiskLevel.CRITICAL
        }

        if (keywordScore >= 12 || containsHighSeverityPhrase(normalizedContext)) {
            Log.i("DecisionEngine", "HIGH keyword override triggered")
            return RiskLevel.HIGH
        }

        val risk = computeHybridRisk(keywordScore, modelConfidence)
        Log.i("DecisionEngine", "Hybrid risk score = $risk")
        return when {
            risk >= 0.75 -> RiskLevel.HIGH
            risk >= 0.5 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun containsCriticalPhrase(context: String): Boolean {
        return criticalPhrases.any { phrase -> context.contains(phrase) }
    }

    fun containsHighSeverityPhrase(context: String): Boolean {
        return highSeverityPhrases.any { phrase -> context.contains(phrase) }
    }

    fun decide(
        context: String,
        keywordMatches: List<KeywordDetector.Match>,
        keywordScore: Int,
        modelConfidence: Double
    ): Decision {
        val riskScore = computeHybridRisk(keywordScore, modelConfidence)
        val level = computeRiskLevel(context, keywordMatches, keywordScore, modelConfidence)
        return Decision(riskScore = riskScore, level = level)
    }

    private fun computeHybridRisk(keywordScore: Int, modelConfidence: Double): Double {
        val kwNorm = if (keywordThreshold <= 0) 0.0 else (keywordScore.toDouble() / keywordThreshold.toDouble())
        val kwClamped = kwNorm.coerceIn(0.0, 1.0)
        val modelClamped = modelConfidence.coerceIn(0.0, 1.0)
        return (0.4 * kwClamped) + (0.6 * modelClamped)
    }
}

