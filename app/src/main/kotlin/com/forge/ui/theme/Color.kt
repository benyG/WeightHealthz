package com.forge.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette de `DESIGN.md` §2, à la valeur près.
 *
 * Les couleurs d'état ne sont pas décoratives : `Mousse` ne s'emploie que lorsque la donnée
 * réelle confirme qu'on est dans la cible, `Brique` que pour les niveaux de retard. Un bouton
 * neutre n'est pas laiton par défaut.
 */
object ForgeColors {
    /** Fond unique de l'app. Jamais de dégradé, nulle part (DESIGN.md §2). */
    val Graphite = Color(0xFF1E1B17)

    /** Surface légèrement surélevée — rare : DESIGN.md §5 préfère les règles aux superpositions. */
    val Charbon = Color(0xFF28241D)

    val Os = Color(0xFFEDE7D9)
    val SableEteint = Color(0xFFA79E8C)

    /** Laiton : progression positive et éléments interactifs actifs. */
    val Laiton = Color(0xFFC39A3C)

    /**
     * Variante éclaircie du laiton, imposée par DESIGN.md §10 : le laiton sur graphite est à la
     * limite du contraste AA. Le laiton reste réservé aux chiffres ≥ 24sp, cette variante sert au
     * texte courant.
     */
    val LaitonClair = Color(0xFFD9B65C)

    /** Uniquement quand la donnée confirme qu'on est dans la cible. */
    val Mousse = Color(0xFF6E8F5C)

    /** Uniquement pour RETARD_1, RETARD_2 et CRITIQUE. */
    val Brique = Color(0xFFA8462D)
}
