package com.example.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface RemoveBgApiService {
    @POST
    suspend fun removeBackground(
        @Url url: String,
        @Body request: RemoveBgRequest
    ): RemoveBgResponse
}
