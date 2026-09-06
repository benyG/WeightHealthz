package com.forge.domain.model

import java.time.LocalDate

data class MealCheck(
    val date: LocalDate,
    val slot: MealSlot,
    val done: Boolean,
)

/**
 * Les six prises quotidiennes de SPEC.md §5.3, dans l'ordre chronologique indicatif.
 *
 * Elles n'ont pas d'ordre imposé entre elles côté UI — DESIGN.md §5 interdit d'ailleurs de les
 * numéroter. L'ordre de déclaration ne sert qu'à l'affichage de la checklist.
 *
 * À noter : DESIGN.md §7.2 dessine une checklist à cinq lignes alors que SPEC.md §5.3 et
 * DESIGN.md §7.1 ("Repas restants : 2/6") en comptent six. On suit ici SPEC.md, qui fait
 * autorité sur les fonctionnalités ; le libellé exact de la sixième prise viendra du plan
 * importé en phase 2.
 */
enum class MealSlot {
    PETIT_DEJEUNER,
    COLLATION_1,
    DEJEUNER,
    COLLATION_2,
    DINER,
    COLLATION_3,
}
