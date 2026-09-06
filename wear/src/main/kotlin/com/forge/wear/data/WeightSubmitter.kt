package com.forge.wear.data

import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enregistre une pesée depuis la montre : envoi au téléphone, mise en attente si celui-ci est
 * hors de portée, et réémission des pesées en attente dès qu'un envoi réussit.
 */
@Singleton
class WeightSubmitter @Inject constructor(
    private val phoneLink: PhoneLink,
    private val pending: PendingWeights,
) {

    enum class Outcome {
        /** Transmise au téléphone. */
        SENT,

        /** Conservée sur la montre : elle partira au prochain contact. */
        QUEUED,
    }

    suspend fun submit(kg: Float, date: LocalDate = LocalDate.now()): Outcome {
        val entry = WeightEntry(date, kg, WeightSource.MANUAL)

        return when (phoneLink.sendWeight(entry)) {
            PhoneLink.SendResult.Sent -> {
                flushPending()
                Outcome.SENT
            }

            PhoneLink.SendResult.PhoneUnreachable -> {
                pending.add(entry)
                Outcome.QUEUED
            }
        }
    }

    /**
     * Rejoue les pesées en attente. Une qui repart en échec est remise en file : on ne perd pas
     * une mesure parce que la liaison a lâché au milieu du rattrapage.
     */
    private suspend fun flushPending() {
        if (pending.isEmpty()) return

        pending.drain().forEach { queued ->
            if (phoneLink.sendWeight(queued) != PhoneLink.SendResult.Sent) {
                pending.add(queued)
            }
        }
    }
}
