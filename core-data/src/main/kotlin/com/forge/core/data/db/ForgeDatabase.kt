package com.forge.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        WeightEntryEntity::class,
        MealCheckEntity::class,
        WorkoutSessionEntity::class,
        ExerciseLogEntity::class,
        SetLogEntity::class,
        AdherenceStateEntity::class,
        WeeklyAnalysisEntity::class,
        PlanTargetEntity::class,
        PlannedWorkoutDayEntity::class,
        PlannedExerciseEntity::class,
        PlannedMealEntity::class,
        PlanMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ForgeConverters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao

    abstract fun mealDao(): MealDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun adherenceDao(): AdherenceDao

    abstract fun weeklyAnalysisDao(): WeeklyAnalysisDao

    abstract fun planDao(): PlanDao

    companion object {
        const val NAME: String = "forge.db"
    }
}

/**
 * Aucune stratégie de migration n'est déclarée ici, et c'est délibéré : le choix entre
 * migrations réelles et repli destructif reste ouvert (DEPLOYMENT.md §13). En version 1 la
 * question ne se pose pas encore ; elle devra être tranchée avant la première version installée
 * avec des données à conserver, pas comblée par un défaut silencieux.
 */
class ForgeConverters {
    @TypeConverter
    fun loadsToString(loads: List<Float>): String = loads.joinToString(separator = ",")

    @TypeConverter
    fun stringToLoads(raw: String): List<Float> =
        if (raw.isBlank()) emptyList() else raw.split(",").map { it.toFloat() }
}
