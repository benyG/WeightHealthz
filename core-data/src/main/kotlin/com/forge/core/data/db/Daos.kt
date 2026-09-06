package com.forge.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entry ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun between(from: Long, to: Long): List<WeightEntryEntity>

    /** `REPLACE` : c'est ce qui rend une relecture de Health Connect sans effet de bord. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<WeightEntryEntity>)

    @Query("SELECT COUNT(*) FROM weight_entry")
    suspend fun count(): Int
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_check WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<List<MealCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(check: MealCheckEntity)
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_session WHERE epochDay = :epochDay")
    fun observeSession(epochDay: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query(
        """
        SELECT s.* FROM workout_session s
        WHERE s.epochDay >= :sinceEpochDay
          AND EXISTS (
            SELECT 1 FROM exercise_log e
            WHERE e.sessionEpochDay = s.epochDay AND e.name = :exerciseName
          )
        ORDER BY s.epochDay
        """,
    )
    suspend fun sessionsWithExercise(exerciseName: String, sinceEpochDay: Long): List<SessionWithExercises>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun sessionsBetween(from: Long, to: Long): List<SessionWithExercises>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertExercise(exercise: ExerciseLogEntity): Long

    @Insert
    suspend fun insertSets(sets: List<SetLogEntity>)

    /** Les exercices partent en cascade, ce qui évite d'accumuler les séries d'une saisie précédente. */
    @Query("DELETE FROM exercise_log WHERE sessionEpochDay = :epochDay")
    suspend fun deleteExercisesOf(epochDay: Long)

    @Query("DELETE FROM workout_session WHERE epochDay = :epochDay")
    suspend fun deleteSession(epochDay: Long)
}

@Dao
interface AdherenceDao {
    @Query("SELECT * FROM adherence_state WHERE id = :id")
    fun observe(id: Int): Flow<AdherenceStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AdherenceStateEntity)
}

@Dao
interface WeeklyAnalysisDao {
    @Query("SELECT * FROM weekly_analysis ORDER BY weekIndex DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklyAnalysisEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(analysis: WeeklyAnalysisEntity)
}

/**
 * Classe abstraite plutôt qu'interface : `replacePlan` porte un corps annoté `@Transaction`, ce
 * que Room gère de façon fiable sur une classe, là où une méthode d'interface avec corps dépend
 * du mode de génération des méthodes par défaut de Kotlin.
 */
@Dao
abstract class PlanDao {
    @Query("SELECT * FROM plan_metadata WHERE id = :id")
    abstract suspend fun metadata(id: Int): PlanMetadataEntity?

    @Query("SELECT * FROM plan_target WHERE weekIndex = :weekIndex")
    abstract suspend fun targetForWeek(weekIndex: Int): PlanTargetEntity?

    @Query("SELECT * FROM plan_target ORDER BY weekIndex")
    abstract suspend fun allTargets(): List<PlanTargetEntity>

    @Query("SELECT COUNT(*) FROM plan_target")
    abstract suspend fun targetCount(): Int

    @Transaction
    @Query("SELECT * FROM planned_workout_day ORDER BY position")
    abstract suspend fun workoutDays(): List<PlannedDayWithExercises>

    @Query("SELECT * FROM planned_meal ORDER BY position")
    abstract suspend fun meals(): List<PlannedMealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMetadata(metadata: PlanMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTargets(targets: List<PlanTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkoutDays(days: List<PlannedWorkoutDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertExercises(exercises: List<PlannedExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMeals(meals: List<PlannedMealEntity>)

    @Query("DELETE FROM plan_target")
    abstract suspend fun clearTargets()

    @Query("DELETE FROM planned_workout_day")
    abstract suspend fun clearWorkoutDays()

    @Query("DELETE FROM planned_meal")
    abstract suspend fun clearMeals()

    /**
     * Remplace tout le contenu du plan en une transaction : un import interrompu ne laisse pas
     * la base avec la moitié d'un programme.
     */
    @Transaction
    open suspend fun replacePlan(
        metadata: PlanMetadataEntity,
        targets: List<PlanTargetEntity>,
        days: List<PlannedWorkoutDayEntity>,
        exercises: List<PlannedExerciseEntity>,
        meals: List<PlannedMealEntity>,
    ) {
        clearTargets()
        clearWorkoutDays() // les exercices suivent en cascade
        clearMeals()
        insertTargets(targets)
        insertWorkoutDays(days)
        insertExercises(exercises)
        insertMeals(meals)
        insertMetadata(metadata)
    }
}
