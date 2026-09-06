package com.forge.core.sync.relay

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation en place tant que le fournisseur de relais n'est pas choisi (DEPLOYMENT.md
 * §11). Elle journalise ce qui **aurait** été annoncé et rend [VoiceRelay.RelayResult.NotConfigured].
 *
 * C'est délibérément une classe visible et non un silence : le jour où le relais manque à
 * l'appel, la trace dit exactement quel message n'est pas parti. Le remplacer par le client
 * webhook réel sera un changement de liaison Hilt, rien d'autre.
 */
@Singleton
class UnconfiguredVoiceRelay @Inject constructor() : VoiceRelay {

    override fun isConfigured(): Boolean = false

    override suspend fun announce(message: String): VoiceRelay.RelayResult {
        Log.i(TAG, "Relais vocal non configuré, message non transmis : \"$message\"")
        return VoiceRelay.RelayResult.NotConfigured
    }

    private companion object {
        const val TAG = "VoiceRelay"
    }
}
