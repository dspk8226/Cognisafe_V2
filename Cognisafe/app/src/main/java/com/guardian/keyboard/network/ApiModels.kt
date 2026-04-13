package com.guardian.keyboard.network

data class PredictRequest(
    val text: String
)

data class PredictResponse(
    val labels: List<String>,
    val probabilities: List<Double>
)

