package com.forge.core.data.mapper

import com.forge.core.data.db.AdherenceStateEntity
import com.forge.core.data.db.ExerciseWithSets
import com.forge.core.data.db.MealCheckEntity
import com.forge.core.data.db.PlanTargetEntity
import com.forge.core.data.db.PlannedDayWithExercises
import com.forge.core.data.db.PlannedMealEntity
import com.forge.core.data.db.SessionWithExercises
import com.forge.core.data.db.WeeklyAnalysisEntity
import com.forge.core.data.db.WeightEntryEntity
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.DayTemplate
import com.forge.domain.model.EscalationLevel
import com.forge.domain.model.ExerciseLog
import com.forge.domain.model.MealCheck
import com.forge.domain.model.MealSlot
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.PlannedExercise
import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog
import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import com.forge.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Conversions entre tables Room et modèles de domaine. Explicites plutôt que réflexives : c'est
 * ici, et nulle part ailleurs, que les deux représentations se rencontrent.
 *
 * Les enums sont stockés par leur nom et relus avec `valueOf` : une valeur inconnue lève au lieu
 * de se replier sur un défaut, parce qu'un niveau d'escalade silencieusement ramené à `A_JOUR`
 * éteindrait des rappels sans que rien ne le signale.
 */

internal fun WeightEntry.toEntity(): WeightEntryEntity =
    WeightEntryEntity(epochDay = date.toEpochDay(), source = source.name, kg = kg)

internal fun WeightEntryEntity.toDomain(): WeightEntry =
    WeightEntry(
        date = LocalDate.ofEpochDay(epochDay),
        kg = kg,
        source = WeightSource.valueOf(source),
    )

internal fun MealCheck.toEntity(): MealCheckEntity =
    MealCheckEntity(epochDay = date.toEpochDay(), slot = slot.name, done = done)

internal fun MealCheckEntity.toDomain(): MealCheck =
    MealCheck(
        date = LocalDate.ofEpochDay(epochDay),
        slot = MealSlot.valueOf(slot),
        done = done,
    )

/**
 * `@Relation` ne garantit pas l'ordre des lignes rapportées : on retrie sur `position`. L'écran
 * de séance affiche "exercice 3 sur 6" et enchaîne les séries dans l'ordre — un ordre aléatoire
 * y serait un bug visible.
 */
internal fun SessionWithExercises.toDomain(): WorkoutSession =
    WorkoutSession(
        date = LocalDate.ofEpochDay(session.epochDay),
        dayTemplate = DayTemplate(session.dayTemplateLabel),
        exercises = exercises.sortedBy { it.exercise.position }.map { it.toDomain() },
    )

internal fun ExerciseWithSets.toDomain(): ExerciseLog =
    ExerciseLog(
        name = exercise.name,
        sets = sets.sortedBy { it.position }.map {
            SetLog(reps = it.reps, weightKg = it.weightKg, cleanTechnique = it.cleanTechnique)
        },
    )

internal fun AdherenceState.toEntity(): AdherenceStateEntity =
    AdherenceStateEntity(streakDays = streakDays, escalationLevel = escalationLevel.name)

internal fun AdherenceStateEntity.toDomain(): AdherenceState =
    AdherenceState(streakDays = streakDays, escalationLevel = EscalationLevel.valueOf(escalationLevel))

internal fun WeeklyAnalysis.toEntity(): WeeklyAnalysisEntity =
    WeeklyAnalysisEntity(
        weekIndex = weekIndex,
        summaryText = summaryText,
        focusExercise = focusExercise,
        audioScript = audioScript,
        audioUrl = audioUrl,
        recommendedAdjustmentKcal = recommendedAdjustmentKcal,
    )

internal fun WeeklyAnalysisEntity.toDomain(): WeeklyAnalysis =
    WeeklyAnalysis(
        weekIndex = weekIndex,
        summaryText = summaryText,
        focusExercise = focusExercise,
        audioScript = audioScript,
        audioUrl = audioUrl,
        recommendedAdjustmentKcal = recommendedAdjustmentKcal,
    )

internal fun PlanTargetEntity.toDomain(): PlanTarget =
    PlanTarget(
        weekIndex = weekIndex,
        targetDeltaKgMin = targetDeltaKgMin,
        targetDeltaKgMax = targetDeltaKgMax,
    )

/**
 * Le contenu planifié se relit en tolérant l'absence d'horaire : un plan qui ne fixe pas de
 * créneau reste un plan valide, il ne produit simplement pas d'événement d'agenda.
 */
internal fun PlannedDayWithExercises.toDomain(): PlannedWorkoutDay =
    PlannedWorkoutDay(
        id = day.id,
        label = day.label,
        exercises = exercises.sortedBy { it.position }.map {
            PlannedExercise(
                name = it.name,
                prescribedSets = it.prescribedSets,
                repRange = RepRange(min = it.repMin, max = it.repMax),
            )
        },
        daysOfWeek = day.daysOfWeek
            .split(",")
            .filter { it.isNotBlank() }
            .map { DayOfWeek.valueOf(it) }
            .toSet(),
        timeOfDay = day.timeOfDay?.let(LocalTime::parse),
    )

internal fun PlannedMealEntity.toDomain(): PlannedMeal =
    PlannedMeal(
        slot = MealSlot.valueOf(slot),
        label = label,
        description = description,
        timeOfDay = timeOfDay?.let(LocalTime::parse),
    )
