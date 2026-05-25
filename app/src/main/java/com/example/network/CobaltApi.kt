package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class CobaltRequest(
    val url: String,
    val videoQuality: String = "1080",
    val filenameStyle: String = "basic",
    val isAudioOnly: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CobaltResponse(
    val status: String,
    val url: String? = null,
    val filename: String? = null,
    val text: String? = null,
    val type: String? = null,
    val error: CobaltError? = null
)

@JsonClass(generateAdapter = true)
data class CobaltError(
    val code: String
)

interface CobaltApi {
    @Headers("Accept: application/json", "Content-Type: application/json", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    @POST("/")
    suspend fun extractVideo(@Body request: CobaltRequest): CobaltResponse
}
