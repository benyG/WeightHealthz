package com.forge.core.sync.relay

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Point d'entrée du service, injecté pour qu'un test puisse le remplacer par un serveur local. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RelayEndpoint

/**
 * Client HTTP du relais, distinct de celui de `core-ai`.
 *
 * Ce n'est pas une commodité : le client de Gemini ajoute la clé API à **chaque** requête qu'il
 * porte. Réutiliser ce client enverrait la clé Gemini au service de notification vocale.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RelayHttpClient

/**
 * Relais vocal via Notify My Alexa (SPEC.md §5.8, DEPLOYMENT.md §11).
 *
 * Un POST JSON portant le message et le code d'accès du compte. Pas de skill Alexa développée
 * ici : `SPEC.md` §5.8 écarte le coût de certification pour un usage mono-utilisateur.
 *
 * **Aucun rejeu différé**, contrairement à l'analyse hebdomadaire. Une annonce est une alerte
 * datée : « trois jours sans rien faire » criée deux jours plus tard est fausse, et une enceinte
 * qui parle du passé apprend à être ignorée. Un échec est rapporté, pas mis en file.
 */
@Singleton
class NotifyMyAlexaRelay @Inject constructor(
    private val credentials: RelayCredentials,
    @RelayEndpoint private val endpoint: String,
    @RelayHttpClient private val client: OkHttpClient,
) : VoiceRelay {

    override fun isConfigured(): Boolean = credentials.accessCode() != null

    override suspend fun announce(message: String): VoiceRelay.RelayResult {
        // Pas de code saisi : ce n'est pas une panne, l'app fonctionne sans enceinte.
        val accessCode = credentials.accessCode() ?: return VoiceRelay.RelayResult.NotConfigured

        return withContext(Dispatchers.IO) {
            try {
                val payload = JSON.encodeToString(
                    Body.serializer(),
                    Body(notification = message, accessCode = accessCode),
                )
                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        VoiceRelay.RelayResult.Delivered
                    } else {
                        // Le code de retour, jamais le corps ni le code d'accès : un motif
                        // d'échec finit toujours par atterrir dans une trace.
                        failure("HTTP ${response.code}")
                    }
                }
            } catch (error: IOException) {
                failure(error.message ?: "réseau indisponible")
            }
        }
    }

    /**
     * Une annonce perdue est silencieuse par nature — personne ne constate qu'une enceinte n'a
     * pas parlé. La trace est le seul endroit où ça se voit.
     */
    private fun failure(reason: String): VoiceRelay.RelayResult {
        Log.w(TAG, "Annonce non transmise à l'enceinte : $reason")
        return VoiceRelay.RelayResult.Failed(reason)
    }

    @Serializable
    private data class Body(val notification: String, val accessCode: String)

    companion object {
        /**
         * Point d'entrée documenté du service. Il est injecté plutôt que codé en dur à l'appel
         * pour qu'un changement côté fournisseur reste une ligne de configuration.
         */
        const val DEFAULT_ENDPOINT: String = "https://api.notifymyecho.com/v1/NotifyMe"

        private const val TAG = "VoiceRelay"

        private val JSON = Json { encodeDefaults = true }
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
