package com.forge.domain.rule

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Index de la semaine du programme. La semaine 1 est celle du démarrage — c'est le numéro que
 * l'app affiche ("Semaine 4/8", DESIGN.md §7.1) et celui qui sélectionne la cible de poids.
 */
object ProgramWeek {

    fun indexFor(start: LocalDate, on: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, on) / 7 + 1).toInt().coerceAtLeast(1)

    /** Fin de la semaine [index] du programme : le jour qui clôt les sept jours. */
    fun endOfWeek(start: LocalDate, index: Int): LocalDate {
        require(index >= 1) { "La première semaine du programme porte l'index 1 (reçu : $index)" }
        return start.plusWeeks(index.toLong()).minusDays(1)
    }
}
