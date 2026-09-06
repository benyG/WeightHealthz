package com.forge.domain.repository

import com.forge.domain.model.AdherenceState
import com.forge.domain.model.MealCheck
import com.forge.domain.model.MealSlot
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WorkoutSession
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Ports du domaine : `core-domain` déclare ce dont les règles ont besoin, `core-data`
 * l'implémente au-dessus de Room et Health Connect (phase 2). La dépendance pointe vers le
 * domaine, jamais l'inverse — c'est ce qui garde ce module testable sans Android.
 *
 * Ces interfaces sont volontairement minces : elles couvrent ce que les phases 2 à 6
 * consomment, et s'étendront avec les besoins réels plutôt qu'en anticipant à vide.
 */

interface WeightRepository {
    fun observeAll(): Flow<List<WeightEntry>>

    suspend fun entriesBetween(from: LocalDate, to: LocalDate): List<WeightEntry>

    suspend fun record(entry: WeightEntry)
}

interface MealRepository {
    fun observeDay(date: LocalDate): Flow<List<MealCheck>>

    suspend fun setChecked(date: LocalDate, slot: MealSlot, done: Boolean)
}

interface WorkoutRepository {
    fun observeSession(date: LocalDate): Flow<WorkoutSession?>

    suspend fun save(session: WorkoutSession)

    /**
     * Efface la séance du jour. Nécessaire parce qu'une séance sans aucun exercice ne doit pas
     * exister : sa seule présence ferait compter la journée comme tenue par le moteur d'escalade,
     * alors que rien n'y a été fait. Défaire sa dernière série efface donc la séance.
     */
    suspend fun delete(date: LocalDate)

    /** Séances contenant [exerciseName], depuis [since] — matière première de la stagnation. */
    suspend fun historyFor(exerciseName: String, since: LocalDate): List<WorkoutSession>

    /** Séances sur une période, bornes incluses — matière première de l'analyse hebdomadaire. */
    suspend fun sessionsBetween(from: LocalDate, to: LocalDate): List<WorkoutSession>
}

interface AdherenceRepository {
    fun observeState(): Flow<AdherenceState>

    suspend fun update(state: AdherenceState)
}

interface WeeklyAnalysisRepository {
    fun observeLatest(): Flow<WeeklyAnalysis?>

    suspend fun save(analysis: WeeklyAnalysis)
}

interface PlanRepository {
    suspend fun targetForWeek(weekIndex: Int): PlanTarget?

    /** Séances prévues par semaine — dénominateur du "séances complétées / planifiées". */
    suspend fun plannedSessionsPerWeek(): Int

    /**
     * Début du programme, d'où se déduit l'index de la semaine en cours. `null` tant qu'aucun
     * plan n'est importé — l'analyse hebdomadaire n'a alors rien à analyser.
     */
    suspend fun programStartDate(): LocalDate?

    /** Journées types du programme, dans l'ordre du plan. */
    suspend fun workoutDays(): List<PlannedWorkoutDay>

    /** Prises quotidiennes du programme, dans l'ordre chronologique indicatif. */
    suspend fun meals(): List<PlannedMeal>

    /** Durée du programme en semaines — le "sur 8" de "Semaine 4 sur 8" (DESIGN.md §7.1). */
    suspend fun programWeekCount(): Int

    /**
     * Charges réellement disponibles sur le matériel, dans l'ordre du plan. Sans elles, la
     * suggestion de palier de SPEC.md §5.4 n'a rien à proposer : liste vide tant qu'aucun plan
     * n'est importé.
     */
    suspend fun availableLoadsKg(): List<Float>
}
