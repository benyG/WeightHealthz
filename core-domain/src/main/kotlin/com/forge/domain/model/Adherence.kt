package com.forge.domain.model

/**
 * Niveaux de la machine d'escalade (SPEC.md §5.5, CLAUDE.md).
 *
 * `A_JOUR` est le `À_JOUR` de la spec, sans accent : l'identifiant sert de clé stable en base et
 * dans les échanges sérialisés. C'est un choix de nommage, pas un changement de règle.
 */
enum class EscalationLevel {
    A_JOUR,
    RETARD_1,
    RETARD_2,
    CRITIQUE;

    /**
     * `RETARD_2` et `CRITIQUE` ne se contentent pas d'une notification Android : ils doivent
     * aussi partir vers l'enceinte via le webhook Alexa (CLAUDE.md). C'est `core-sync` qui
     * réagit (phase 4), mais la décision appartient au domaine.
     */
    val requiresVoiceRelay: Boolean
        get() = this == RETARD_2 || this == CRITIQUE
}

data class AdherenceState(
    val streakDays: Int,
    val escalationLevel: EscalationLevel,
) {
    init {
        require(streakDays >= 0) { "Un streak ne peut pas être négatif (reçu : $streakDays)" }
    }

    companion object {
        /** État initial : à jour, aucun jour capitalisé. */
        val START = AdherenceState(streakDays = 0, escalationLevel = EscalationLevel.A_JOUR)
    }
}
