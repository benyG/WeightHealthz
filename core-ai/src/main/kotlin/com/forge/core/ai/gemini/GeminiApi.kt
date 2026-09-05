package com.forge.core.ai.gemini

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

internal interface GeminiApi {

    /**
     * La clé d'API voyage dans l'en-tête `x-goog-api-key`, posé par un intercepteur, et non en
     * paramètre d'URL : une URL se retrouve dans les logs et les rapports de crash, pas un
     * en-tête que le log HTTP de niveau BASIC n'imprime pas.
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequest,
    ): GeminiResponse

    companion object {
        const val BASE_URL: String = "https://generativelanguage.googleapis.com/"

        /** Seul endroit où le modèle est nommé. */
        const val DEFAULT_MODEL: String = "gemini-2.5-flash"
    }
}
