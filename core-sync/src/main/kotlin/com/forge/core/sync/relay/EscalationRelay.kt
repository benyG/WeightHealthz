package com.forge.core.sync.relay

import com.forge.domain.model.AdherenceState
import com.forge.domain.rule.EscalationMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fait le pont entre la machine d'escalade et l'enceinte.
 *
 * Aucune logique d'état ici : c'est `core-domain` qui décide des transitions et, via
 * `EscalationLevel.requiresVoiceRelay`, lesquelles méritent la voix (`RETARD_2` et `CRITIQUE`,
 * cf. `CLAUDE.md`). Ce module ne fait que réagir — dupliquer la décision ici serait la
 * condamner à diverger.
 */
@Singleton
class EscalationRelay @Inject constructor(
    private val relay: VoiceRelay,
) {

    /**
     * Relaie l'état si son niveau l'exige. Le message est celui du domaine, identique à celui de
     * la notification Android : `DESIGN.md` §9 impose le même vocabulaire d'un canal à l'autre.
     *
     * Renvoie `null` quand le niveau ne demande pas de voix — cas normal, pas un échec.
     */
    suspend fun relayIfNeeded(state: AdherenceState, missedItem: String): VoiceRelay.RelayResult? {
        if (!state.escalationLevel.requiresVoiceRelay) return null
        val message = EscalationMessage.forLevel(state.escalationLevel, missedItem) ?: return null
        return relay.announce(message)
    }

    /**
     * Rappel programmé (repas, séance) relayé vers l'enceinte **en plus** de la notification
     * Android, jamais à sa place — SPEC.md §5.8 veut le programme relayé partout, pas déplacé.
     */
    suspend fun relayScheduledReminder(message: String): VoiceRelay.RelayResult = relay.announce(message)
}
