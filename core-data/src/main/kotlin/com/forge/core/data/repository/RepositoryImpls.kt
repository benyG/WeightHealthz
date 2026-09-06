package com.forge.core.data.repository

import com.forge.core.data.db.AdherenceDao
import com.forge.core.data.db.AdherenceStateEntity
import com.forge.core.data.db.ExerciseLogEntity
import com.forge.core.data.db.MealDao
import com.forge.core.data.db.PlanDao
import com.forge.core.data.db.PlanMetadataEntity
import com.forge.core.data.db.SetLogEntity
import com.forge.core.data.db.WeeklyAnalysisDao
import com.forge.core.data.db.WeightDao
import com.forge.core.data.db.WorkoutDao
import com.forge.core.data.db.WorkoutSessionEntity
import com.forge.core.data.mapper.toDomain
import com.forge.core.data.mapper.toEntity
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.MealCheck
import com.forge.domain.model.MealSlot
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WorkoutSession
import com.forge.domain.repository.AdherenceRepository
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WeeklyAnalysisRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomWeightRepository @Inject constructor(
    private val dao: WeightDao,
) : WeightRepository {

    override fun observeAll(): Flow<List<WeightEntry>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun entriesBetween(from: LocalDate, to: LocalDate): List<WeightEntry> =
        dao.between(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    override suspend fun record(entry: WeightEntry) = dao.upsert(listOf(entry.toEntity()))
}

@Singleton
class RoomMealRepository @Inject constructor(
    private val dao: MealDao,
) : MealRepository {

    override fun observeDay(date: LocalDate): Flow<List<MealCheck>> =
        dao.observeDay(date.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun setChecked(date: LocalDate, slot: MealSlot, done: Boolean) =
        dao.upsert(MealCheck(date, slot, done).toEntity())
}

@Singleton
class RoomWorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {

    override fun observeSession(date: LocalDate): Flow<WorkoutSession?> =
        dao.observeSession(date.toEpochDay()).map { it?.toDomain() }

    /**
     * Réécrit la séance entière : les exercices existants sont supprimés (les séries suivent en
     * cascade) avant réinsertion. Sans cela, corriger une série en ajouterait une de plus.
     */
    override suspend fun save(session: WorkoutSession) {
        dao.insertSession(
            WorkoutSessionEntity(
                epochDay = session.date.toEpochDay(),
                dayTemplateLabel = session.dayTemplate.label,
            ),
        )
        dao.deleteExercisesOf(session.date.toEpochDay())
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = dao.insertExercise(
                ExerciseLogEntity(
                    sessionEpochDay = session.date.toEpochDay(),
                    name = exercise.name,
                    position = exerciseIndex,
                ),
            )
            dao.insertSets(
                exercise.sets.mapIndexed { setIndex, set ->
                    SetLogEntity(
                        exerciseLogId = exerciseId,
                        position = setIndex,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        cleanTechnique = set.cleanTechnique,
                    )
                },
            )
        }
    }

    /** Les exercices et leurs séries suivent en cascade. */
    override suspend fun delete(date: LocalDate) = dao.deleteSession(date.toEpochDay())

    override suspend fun historyFor(exerciseName: String, since: LocalDate): List<WorkoutSession> =
        dao.sessionsWithExercise(exerciseName, since.toEpochDay()).map { it.toDomain() }

    override suspend fun sessionsBetween(from: LocalDate, to: LocalDate): List<WorkoutSession> =
        dao.sessionsBetween(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }
}

@Singleton
class RoomAdherenceRepository @Inject constructor(
    private val dao: AdherenceDao,
) : AdherenceRepository {

    /** Base vierge : l'état de départ du domaine, pas un état vide inventé ici. */
    override fun observeState(): Flow<AdherenceState> =
        dao.observe(AdherenceStateEntity.SINGLETON_ID).map { it?.toDomain() ?: AdherenceState.START }

    override suspend fun update(state: AdherenceState) = dao.upsert(state.toEntity())
}

@Singleton
class RoomWeeklyAnalysisRepository @Inject constructor(
    private val dao: WeeklyAnalysisDao,
) : WeeklyAnalysisRepository {

    override fun observeLatest(): Flow<WeeklyAnalysis?> =
        dao.observeLatest().map { it?.toDomain() }

    override suspend fun save(analysis: WeeklyAnalysis) = dao.upsert(analysis.toEntity())
}

@Singleton
class RoomPlanRepository @Inject constructor(
    private val dao: PlanDao,
) : PlanRepository {

    override suspend fun targetForWeek(weekIndex: Int): PlanTarget? =
        dao.targetForWeek(weekIndex)?.toDomain()

    /** Zéro tant qu'aucun plan n'est importé : on ne devine pas un programme absent. */
    override suspend fun plannedSessionsPerWeek(): Int =
        dao.metadata(PlanMetadataEntity.SINGLETON_ID)?.sessionsPerWeek ?: 0

    override suspend fun programStartDate(): LocalDate? =
        dao.metadata(PlanMetadataEntity.SINGLETON_ID)?.let { LocalDate.ofEpochDay(it.programStartEpochDay) }

    override suspend fun workoutDays(): List<PlannedWorkoutDay> = dao.workoutDays().map { it.toDomain() }

    override suspend fun meals(): List<PlannedMeal> = dao.meals().map { it.toDomain() }

    override suspend fun programWeekCount(): Int = dao.targetCount()

    /** Liste vide tant qu'aucun plan n'est importé : un râtelier inventé proposerait de faux paliers. */
    override suspend fun availableLoadsKg(): List<Float> =
        dao.metadata(PlanMetadataEntity.SINGLETON_ID)?.availableLoadsKg ?: emptyList()
}
