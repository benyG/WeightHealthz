package com.forge.core.data.plan

import android.content.Context
import com.forge.core.data.db.PlanDao
import com.forge.core.data.db.PlanMetadataEntity
import com.forge.core.data.db.PlanTargetEntity
import com.forge.core.data.db.PlannedExerciseEntity
import com.forge.core.data.db.PlannedMealEntity
import com.forge.core.data.db.PlannedWorkoutDayEntity
import com.forge.domain.model.MealSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Charge le plan bundlé en base au premier lancement (SPEC.md §5.1).
 *
 * L'opération est idempotente : c'est la version enregistrée en base qui décide, pas le fait que
 * la base soit vide. Relancer l'app cent fois n'importe qu'une fois ; livrer une version
 * supérieure du fichier remplace le contenu au lancement suivant.
 */
@Singleton
class PlanImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val planDao: PlanDao,
    private val json: Json,
) {

    sealed interface Outcome {
        /** Rien à faire : la base porte déjà cette version. */
        data class AlreadyImported(val version: Int) : Outcome

        data class Imported(val version: Int) : Outcome

        data class Replaced(val from: Int, val to: Int) : Outcome
    }

    suspend fun importIfNeeded(
        assetName: String = DEFAULT_ASSET,
        today: LocalDate = LocalDate.now(),
    ): Outcome = withContext(Dispatchers.IO) {
        val raw = context.assets.open(assetName).bufferedReader().use { it.readText() }
        importFrom(raw, assetName, today)
    }

    /**
     * Cœur de l'import, séparé de la lecture du fichier : c'est ce qui permet de tester le
     * parsing, la validation et l'idempotence sans dépendre de l'`AssetManager`.
     */
    internal suspend fun importFrom(
        raw: String,
        label: String = DEFAULT_ASSET,
        today: LocalDate = LocalDate.now(),
    ): Outcome {
        val plan = json.decodeFromString(PlanJson.serializer(), raw)
        validate(plan, label)

        val current = planDao.metadata(PlanMetadataEntity.SINGLETON_ID)
        return when {
            current == null -> {
                write(plan, today)
                Outcome.Imported(plan.version)
            }

            current.version == plan.version -> Outcome.AlreadyImported(plan.version)

            else -> {
                write(plan, today)
                Outcome.Replaced(from = current.version, to = plan.version)
            }
        }
    }

    /**
     * Un plan mal formé doit échouer à l'import, bruyamment, plutôt que de produire une
     * checklist amputée ou une séance sans exercice qu'on ne remarquerait qu'à l'usage.
     */
    private fun validate(plan: PlanJson, assetName: String) {
        val knownSlots = MealSlot.entries.map { it.name }.toSet()
        val unknownSlots = plan.meals.map { it.slot }.filterNot { it in knownSlots }
        require(unknownSlots.isEmpty()) {
            "$assetName : créneau de repas inconnu ${unknownSlots.joinToString()} — attendu parmi $knownSlots"
        }

        val duplicateSlots = plan.meals.groupingBy { it.slot }.eachCount().filterValues { it > 1 }.keys
        require(duplicateSlots.isEmpty()) {
            "$assetName : créneau de repas en double ${duplicateSlots.joinToString()}"
        }

        val duplicateDays = plan.workoutDays.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateDays.isEmpty()) {
            "$assetName : journée de séance en double ${duplicateDays.joinToString()}"
        }

        plan.workoutDays.forEach { day ->
            day.exercises.forEach { exercise ->
                require(exercise.prescribedSets >= 1) {
                    "$assetName : ${day.id}/${exercise.name} prescrit ${exercise.prescribedSets} série(s)"
                }
                require(exercise.repMin in 1..exercise.repMax) {
                    "$assetName : ${day.id}/${exercise.name} a une fourchette de reps invalide " +
                        "(${exercise.repMin}–${exercise.repMax})"
                }
            }
        }

        plan.weeklyTargets.forEach { target ->
            require(target.targetDeltaKgMin <= target.targetDeltaKgMax) {
                "$assetName : semaine ${target.weekIndex} a une fourchette de poids inversée"
            }
        }
    }

    private suspend fun write(plan: PlanJson, today: LocalDate) {
        planDao.replacePlan(
            metadata = PlanMetadataEntity(
                version = plan.version,
                importedAtEpochDay = today.toEpochDay(),
                availableLoadsKg = plan.availableLoadsKg,
            ),
            targets = plan.weeklyTargets.map {
                PlanTargetEntity(
                    weekIndex = it.weekIndex,
                    targetDeltaKgMin = it.targetDeltaKgMin,
                    targetDeltaKgMax = it.targetDeltaKgMax,
                )
            },
            days = plan.workoutDays.mapIndexed { index, day ->
                PlannedWorkoutDayEntity(id = day.id, label = day.label, position = index)
            },
            exercises = plan.workoutDays.flatMap { day ->
                day.exercises.mapIndexed { index, exercise ->
                    PlannedExerciseEntity(
                        dayId = day.id,
                        name = exercise.name,
                        prescribedSets = exercise.prescribedSets,
                        repMin = exercise.repMin,
                        repMax = exercise.repMax,
                        position = index,
                    )
                }
            },
            meals = plan.meals.mapIndexed { index, meal ->
                PlannedMealEntity(
                    slot = meal.slot,
                    label = meal.label,
                    description = meal.description,
                    position = index,
                )
            },
        )
    }

    companion object {
        const val DEFAULT_ASSET: String = "plan.json"
    }
}
