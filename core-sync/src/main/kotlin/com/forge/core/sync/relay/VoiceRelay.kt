package com.forge.core.sync.relay

/**
 * Relais vocal vers l'enceinte de la maison (SPEC.md §5.8).
 *
 * Le fournisseur retenu est Notify My Alexa (DEPLOYMENT.md §11), implémenté par
 * `NotifyMyAlexaRelay`. L'interface reste, et c'est ce qui rend le choix réversible : en changer
 * ne touche qu'une liaison Hilt, le reste de l'app parlant à ce contrat.
 *
 * SPEC.md §5.8 exclut de développer une skill Alexa : le coût de certification est
 * disproportionné pour un usage mono-utilisateur.
 */
interface VoiceRelay {

    suspend fun announce(message: String): RelayResult

    /**
     * Le relais est-il utilisable ? Question posée séparément de [announce] à dessein : sonder
     * l'état en annonçant un message vide ferait réellement parler l'enceinte le jour où un vrai
     * relais est branché.
     */
    fun isConfigured(): Boolean

    sealed interface RelayResult {
        data object Delivered : RelayResult

        /** Aucun relais configuré : ce n'est pas une panne, l'app fonctionne sans. */
        data object NotConfigured : RelayResult

        data class Failed(val reason: String) : RelayResult
    }
}
