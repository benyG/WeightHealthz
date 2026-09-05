package com.forge.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Tables Room. Ces types sont distincts des modèles de `core-domain` : le schéma de stockage
 * peut évoluer (index, colonnes dénormalisées, migrations) sans déformer le modèle métier, et
 * `core-domain` reste ignorant de Room. La conversion vit dans `Mappers.kt`.
 */

/**
 * Une pesée par jour **et par source** : réimporter Health Connect ne crée pas de doublon, et
 * re-saisir un poids à la main corrige la valeur du jour au lieu d'en empiler une deuxième.
 * Quand les deux sources existent le même jour, la moyenne mobile du domaine les moyenne.
 */
@Entity(tableName = "weight_entry", primaryKeys = ["epochDay", "source"])
data class WeightEntryEntity(
    val epochDay: Long,
    val source: String,
    val kg: Float,
)

@Entity(tableName = "meal_check", primaryKeys = ["epochDay", "slot"])
data class MealCheckEntity(
    val epochDay: Long,
    val slot: String,
    val done: Boolean,
)

@Entity(tableName = "workout_session")
data class WorkoutSessionEntity(
    @PrimaryKey val epochDay: Long,
    val dayTemplateLabel: String,
)

@Entity(
    tableName = "exercise_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["epochDay"],
            childColumns = ["sessionEpochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionEpochDay")],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionEpochDay: Long,
    val name: String,
    /** Ordre dans la séance : l'écran de montre affiche "exercice 3 sur 6" (DESIGN.md §7.3). */
    val position: Int,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseLogId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseLogId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Float,
    val cleanTechnique: Boolean,
)

/** Ligne unique : l'app est mono-utilisateur, il n'y a qu'un état d'adhérence. */
@Entity(tableName = "adherence_state")
data class AdherenceStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val streakDays: Int,
    val escalationLevel: String,
) {
    companion object {
        const val SINGLETON_ID: Int = 0
    }
}

@Entity(tableName = "weekly_analysis")
data class WeeklyAnalysisEntity(
    @PrimaryKey val weekIndex: Int,
    val summaryText: String,
    val audioUrl: String?,
    val recommendedAdjustmentKcal: Int,
)

// --- Contenu du plan importé au premier lancement (SPEC.md §5.1) ---

@Entity(tableName = "plan_target")
data class PlanTargetEntity(
    @PrimaryKey val weekIndex: Int,
    val targetDeltaKgMin: Float,
    val targetDeltaKgMax: Float,
)

@Entity(tableName = "planned_workout_day")
data class PlannedWorkoutDayEntity(
    @PrimaryKey val id: String,
    val label: String,
    val position: Int,
)

@Entity(
    tableName = "planned_exercise",
    foreignKeys = [
        ForeignKey(
            entity = PlannedWorkoutDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dayId")],
)
data class PlannedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: String,
    val name: String,
    val prescribedSets: Int,
    val repMin: Int,
    val repMax: Int,
    val position: Int,
)

@Entity(tableName = "planned_meal")
data class PlannedMealEntity(
    @PrimaryKey val slot: String,
    val label: String,
    val description: String,
    val position: Int,
)

/**
 * Trace de l'import : c'est elle qui rend l'opération idempotente. Un relancement de l'app avec
 * la même version de plan ne réimporte rien ; une version différente remplace le contenu.
 */
@Entity(tableName = "plan_metadata")
data class PlanMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val version: Int,
    val importedAtEpochDay: Long,
    /** Charges réellement disponibles, pour la suggestion de palier (SPEC.md §5.4). */
    val availableLoadsKg: List<Float>,
) {
    companion object {
        const val SINGLETON_ID: Int = 0
    }
}

// --- Projections de lecture ---

data class ExerciseWithSets(
    @Embedded val exercise: ExerciseLogEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseLogId")
    val sets: List<SetLogEntity>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = ExerciseLogEntity::class, parentColumn = "epochDay", entityColumn = "sessionEpochDay")
    val exercises: List<ExerciseWithSets>,
)

data class PlannedDayWithExercises(
    @Embedded val day: PlannedWorkoutDayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId")
    val exercises: List<PlannedExerciseEntity>,
)
