package com.forge.domain.repository

import com.forge.domain.model.AdherenceState
import com.forge.domain.model.MealCheck
import com.forge.domain.model.MealSlot
import com.forge.domain.model.PlanTarget
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

    /** Séances contenant [exerciseName], depuis [since] — matière première de la stagnation. */
    suspend fun historyFor(exerciseName: String, since: LocalDate): List<WorkoutSession>
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
}
