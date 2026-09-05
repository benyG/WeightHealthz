package com.forge.core.sync.relay

/**
 * Relais vocal vers l'enceinte de la maison (SPEC.md §5.8).
 *
 * L'interface existe avant son implémentation parce que le **fournisseur reste à trancher** —
 * Notify-My-Alexa ou un applet IFTTT (DEPLOYMENT.md §11). Les deux se réduisent à un POST HTTP
 * vers une URL personnelle ; ce qui change est le format du corps et le compte à créer, pas la
 * façon dont le reste de l'app appelle.
 *
 * SPEC.md §5.8 exclut de développer une skill Alexa : le coût de certification est
 * disproportionné pour un usage mono-utilisateur.
 */
interface VoiceRelay {

    suspend fun announce(message: String): RelayResult

    sealed interface RelayResult {
        data object Delivered : RelayResult

        /** Aucun relais configuré : ce n'est pas une panne, l'app fonctionne sans. */
        data object NotConfigured : RelayResult

        data class Failed(val reason: String) : RelayResult
    }
}
