package com.guardian.keyboard.network

import retrofit2.http.Body
import retrofit2.http.POST

interface CogniSafeApi {
    @POST("/predict")
    suspend fun predict(@Body req: PredictRequest): PredictResponse
}

