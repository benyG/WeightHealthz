package com.forge.work

import com.forge.core.sync.relay.EscalationRelay
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.EscalationLevel
import com.forge.domain.repository.AdherenceRepository
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import com.forge.domain.rule.EscalationMachine
import com.forge.domain.rule.EscalationMessage
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Poste une notification d'escalade. Interface pour que la convergence des canaux se teste. */
interface EscalationNotifier {
    fun notifyEscalation(level: EscalationLevel, message: String)
}

/**
 * Clôture d'une journée : avance la machine d'escalade, puis prévient sur **tous** les canaux
 * que le niveau atteint réclame.
 *
 * Extrait du worker à dessein. C'est le point de convergence exigé par SPEC.md §10 — un passage
 * en `CRITIQUE` doit notifier l'app *et* déclencher le relais vocal dans le même événement — et
 * cette exigence mérite un test qui tourne sans émulateur ni WorkManager.
 */
@Singleton
class EscalationRunner @Inject constructor(
    private val weights: WeightRepository,
    private val meals: MealRepository,
    private val workouts: WorkoutRepository,
    private val adherence: AdherenceRepository,
    private val notifier: EscalationNotifier,
    private val relay: EscalationRelay,
) {

    suspend fun closeDay(today: LocalDate = LocalDate.now()): AdherenceState {
        val next = EscalationMachine.onDayClosed(
            state = adherence.observeState().first(),
            hadValidAction = hadValidAction(today),
        )
        adherence.update(next)

        val message = EscalationMessage.forLevel(next.escalationLevel, RE_ENTRY_POINT)

        if (message != null) {
            notifier.notifyEscalation(next.escalationLevel, message)
            // Même événement, même message. `relayIfNeeded` ne parle que pour RETARD_2 et
            // CRITIQUE : la décision appartient au domaine, elle n'est pas rejouée ici.
            relay.relayIfNeeded(next, RE_ENTRY_POINT)
        }

        return next
    }

    /** Pesée, repas coché ou séance loguée : n'importe laquelle remet le compteur à zéro. */
    private suspend fun hadValidAction(today: LocalDate): Boolean =
        weights.entriesBetween(today, today).isNotEmpty() ||
            meals.observeDay(today).first().any { it.done } ||
            workouts.observeSession(today).first() != null

    private companion object {
        /**
         * Ce que le message nomme.
         *
         * Une journée qui escalade est une journée où **rien** n'a été fait : n'importe quelle
         * action l'aurait tenue. Énumérer tout ce qui manque serait une liste de reproches qui ne
         * dit pas quoi faire maintenant ; le message nomme donc la pesée, le geste le plus court
         * pour repartir — un tap depuis la Tile.
         */
        const val RE_ENTRY_POINT = "Pesée"
    }
}
