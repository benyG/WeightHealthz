package com.forge.wear.data

import com.forge.domain.link.WearSetEntry
import com.forge.domain.model.SetLog
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transmet une série saisie au poignet : envoi au téléphone, mise en attente s'il est hors de
 * portée, et réémission de ce qui attend dès qu'un envoi réussit.
 *
 * Même forme que `WeightSubmitter`, et pour la même raison : la montre ne perd pas une saisie
 * parce que le téléphone est resté au vestiaire.
 */
@Singleton
class SetSubmitter @Inject constructor(
    private val phoneLink: PhoneLink,
    private val pending: PendingSets,
) {

    enum class Outcome {
        /** Transmise au téléphone. */
        SENT,

        /** Conservée sur la montre : elle partira au prochain contact. */
        QUEUED,
    }

    /**
     * La série est mise en file **avant** d'être envoyée, à la différence d'une pesée.
     *
     * Une pesée se valide et l'écran se ferme en attendant l'envoi ; une série se valide au
     * milieu d'une séance, et rien n'empêche de baisser le poignet ou de quitter l'écran dans la
     * seconde. Écrire d'abord, envoyer ensuite, c'est la seule façon de ne pas perdre une série
     * parce que la portée qui portait l'envoi a été annulée.
     */
    suspend fun submit(
        exerciseName: String,
        position: Int,
        set: SetLog,
        date: LocalDate = LocalDate.now(),
    ): Outcome {
        pending.add(WearSetEntry(date, exerciseName, position, set))

        return if (flushPending()) Outcome.SENT else Outcome.QUEUED
    }

    /**
     * Vide la file vers le téléphone. Une série qui repart en échec y est remise : on ne perd pas
     * une série parce que la liaison a lâché au milieu du rattrapage.
     */
    private suspend fun flushPending(): Boolean {
        var allSent = true

        pending.drain().forEach { queued ->
            if (phoneLink.sendSet(queued) != PhoneLink.SendResult.Sent) {
                pending.add(queued)
                allSent = false
            }
        }

        return allSent
    }
}
