package com.forge.domain

/**
 * Constantes du programme, reprises telles quelles du plan original (CLAUDE.md, section
 * "Règles métier non-négociables").
 *
 * Elles sont isolées ici pour qu'une modification soit un choix explicite et visible dans un
 * diff, jamais l'effet de bord d'un refactor : `ForgeRulesTest` échoue si l'une de ces valeurs
 * change. Les fonctions qui les appliquent (moyenne mobile, ajustement calorique, escalade,
 * double progression) arrivent en phase 1 — voir IMPLEMENTATION_PLAN.md §3.
 */
object ForgeRules {

    /** Fenêtre de la moyenne mobile du poids : aucune décision ne se prend sur un poids isolé. */
    const val MOVING_AVERAGE_DAYS: Int = 7

    /** Prise de poids visée, en kg par semaine. */
    const val WEEKLY_GAIN_TARGET_MIN_KG: Double = 0.3
    const val WEEKLY_GAIN_TARGET_MAX_KG: Double = 0.5

    /** En dessous de ce gain hebdomadaire, on ajoute [KCAL_ADJUSTMENT_UP]. */
    const val WEEKLY_GAIN_LOW_THRESHOLD_KG: Double = 0.2

    /** Au-dessus de ce gain hebdomadaire, on applique [KCAL_ADJUSTMENT_DOWN]. */
    const val WEEKLY_GAIN_HIGH_THRESHOLD_KG: Double = 0.7

    /** Un ajustement ne se déclenche qu'après ce nombre de semaines consécutives hors cible. */
    const val CONSECUTIVE_WEEKS_BEFORE_ADJUSTMENT: Int = 2

    /** Ajustement appliqué quand le gain reste sous [WEEKLY_GAIN_LOW_THRESHOLD_KG]. */
    const val KCAL_ADJUSTMENT_UP: Int = 250

    /** Ajustement appliqué quand le gain dépasse [WEEKLY_GAIN_HIGH_THRESHOLD_KG]. */
    const val KCAL_ADJUSTMENT_DOWN: Int = -200

    /**
     * Borne de validation de tout ajustement calorique, y compris celui proposé par Gemini :
     * la sortie du modèle est tronquée à cet intervalle avant d'être appliquée (SPEC.md §6.3).
     */
    const val KCAL_ADJUSTMENT_BOUND: Int = 300

    /** Jours d'inaction menant à `RETARD_1`, `RETARD_2` puis `CRITIQUE` (SPEC.md §5.5). */
    const val ESCALATION_DAYS_LEVEL_1: Int = 1
    const val ESCALATION_DAYS_LEVEL_2: Int = 2
    const val ESCALATION_DAYS_CRITICAL: Int = 3

    /** Semaines sans progression sur un exercice avant de le signaler comme stagnant (SPEC.md §5.4). */
    const val STAGNATION_WEEKS: Int = 2
}
