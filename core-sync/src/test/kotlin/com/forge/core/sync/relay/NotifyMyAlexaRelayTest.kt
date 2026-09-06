package com.forge.core.sync.relay

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le relais vocal face à un vrai serveur, local.
 *
 * Ce qui est vérifié n'est pas « ça marche » mais ce dont dépend la confiance dans le canal :
 * que le message parte tel quel, que le code d'accès ne fuie pas dans un message d'erreur, et
 * qu'un service en panne n'entraîne pas l'application avec lui.
 */
class NotifyMyAlexaRelayTest {

    private lateinit var server: MockWebServer

    /** Doublure du stockage : le vrai s'appuie sur les préférences Android. */
    private class FakeCredentials(private val code: String?) : RelayCredentials {
        override fun accessCode(): String? = code
        override fun store(accessCode: String) = Unit
        override fun clear() = Unit
    }

    private fun relay(code: String? = "code-de-test") = NotifyMyAlexaRelay(
        credentials = FakeCredentials(code),
        endpoint = server.url("/v1/NotifyMe").toString(),
        client = OkHttpClient(),
    )

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        // Un test éteint déjà le serveur pour simuler un service injoignable ; le rallumer pour
        // l'éteindre proprement n'apporterait rien.
        runCatching { server.shutdown() }
    }

    @Test
    fun `le message part tel quel, avec le code d acces`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = relay().announce("Trois jours sans rien enregistrer.")

        assertEquals(VoiceRelay.RelayResult.Delivered, result)

        val body = server.takeRequest().body.readUtf8()
        // Le texte est celui du domaine : DESIGN.md §9 impose le même vocabulaire d'un canal à
        // l'autre, une reformulation ici le romprait.
        assertTrue(body.contains("Trois jours sans rien enregistrer."))
        assertTrue(body.contains("code-de-test"))
    }

    @Test
    fun `sans code saisi le relais ne parle pas et ne tente rien`() = runTest {
        val result = relay(code = null).announce("Message.")

        assertEquals(VoiceRelay.RelayResult.NotConfigured, result)
        // Aucun appel : un relais non configuré n'est pas une panne à faire remonter au réseau.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `un service en panne est un echec rapporte, pas une exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = relay().announce("Message.")

        assertTrue(result is VoiceRelay.RelayResult.Failed)
    }

    @Test
    fun `le code d acces ne se retrouve pas dans le motif d echec`() = runTest {
        // Le service renvoie parfois l'erreur avec la requête ; le motif ne doit porter que le
        // code de retour, parce qu'un motif d'échec finit toujours dans une trace.
        server.enqueue(MockResponse().setResponseCode(401).setBody("accessCode code-de-test invalide"))

        val result = relay().announce("Message.")

        val reason = (result as VoiceRelay.RelayResult.Failed).reason
        assertFalse(reason.contains("code-de-test"))
        assertTrue(reason.contains("401"))
    }

    @Test
    fun `un service injoignable ne fait pas tomber l app`() = runTest {
        server.shutdown()

        val result = relay().announce("Message.")

        // L'escalade doit rester utilisable sans enceinte : la notification Android, elle, est
        // déjà partie.
        assertTrue(result is VoiceRelay.RelayResult.Failed)
    }

    @Test
    fun `le relais se declare configure des qu un code existe`() {
        assertTrue(relay().isConfigured())
        assertFalse(relay(code = null).isConfigured())
    }
}
