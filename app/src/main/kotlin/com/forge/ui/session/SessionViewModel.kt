package com.forge.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.domain.model.DayTemplate
import com.forge.domain.model.ExerciseLog
import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog
import com.forge.domain.model.WorkoutSession
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WorkoutRepository
import com.forge.domain.rule.DoubleProgression
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une série loguée, telle qu'elle s'affiche et se corrige. */
data class SetRow(
    val position: Int,
    val reps: Int,
    val weightKg: Float,
    val cleanTechnique: Boolean,
)

data class ExerciseRow(
    val name: String,
    val prescribedSets: Int,
    val repRange: RepRange,
    val sets: List<SetRow>,
    /**
     * Charge à charger aujourd'hui, déduite de la dernière séance par la double progression.
     * `null` la première fois qu'on fait l'exercice : la charge de départ appartient à la
     * personne, pas à une règle.
     */
    val suggestedLoadKg: Float?,
    /**
     * Palier gagné pour la **prochaine** séance, une fois toutes les séries du jour loguées au
     * haut de la fourchette avec une technique propre. `null` tant qu'il n'est pas gagné.
     */
    val earnedNextLoadKg: Float?,
) {
    val done: Boolean get() = sets.size >= prescribedSets
}

data class SessionUiState(
    val label: String? = null,
    val exercises: List<ExerciseRow> = emptyList(),
    val selected: String? = null,
    val hasPlan: Boolean = true,
    val loaded: Boolean = false,
) {
    val current: ExerciseRow? get() = exercises.firstOrNull { it.name == selected }
}

/**
 * Écran de séance active (SPEC.md §5.4).
 *
 * Chaque série validée est écrite immédiatement : une séance se fait en salle, avec des
 * interruptions, et rien ne doit dépendre d'un bouton « terminer » qu'on oublie de presser.
 * `WorkoutRepository.save` réécrit la séance entière, donc chaque geste part de l'état persisté
 * plutôt que d'un brouillon en mémoire.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val plans: PlanRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    /** Exercice choisi à la main ; `null` tant que l'écran suit l'ordre du plan. */
    private val selection = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionUiState> =
        combine(workouts.observeSession(today), selection) { logged, chosen -> logged to chosen }
            .mapLatest { (logged, chosen) -> buildState(logged, chosen) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SessionUiState())

    fun select(exerciseName: String) {
        selection.value = exerciseName
    }

    /**
     * Valide une série sur l'exercice affiché.
     *
     * [cleanTechnique] vient d'une case cochée à la main, jamais d'un défaut : la double
     * progression n'accorde un palier que sur des séries jugées propres, et ce jugement
     * n'appartient pas à l'app.
     */
    fun logSet(reps: Int, weightKg: Float, cleanTechnique: Boolean) {
        val exercise = state.value.selected ?: return
        mutate(exercise) { it + SetLog(reps = reps, weightKg = weightKg, cleanTechnique = cleanTechnique) }
    }

    /** Corrige le jugement de technique après coup — la case reste modifiable série par série. */
    fun toggleTechnique(exerciseName: String, position: Int) {
        mutate(exerciseName) { sets ->
            sets.mapIndexed { index, set ->
                if (index == position) set.copy(cleanTechnique = !set.cleanTechnique) else set
            }
        }
    }

    /** Défait la dernière série d'un exercice : une saisie ratée se répare sans quitter l'écran. */
    fun undoLastSet(exerciseName: String) {
        mutate(exerciseName) { it.dropLast(1) }
    }

    private fun mutate(exerciseName: String, transform: (List<SetLog>) -> List<SetLog>) {
        viewModelScope.launch {
            val label = state.value.label ?: return@launch
            val existing = workouts.observeSession(today).first()?.exercises.orEmpty()
            val bySets = existing.associate { it.name to it.sets }.toMutableMap()

            val updated = transform(bySets[exerciseName].orEmpty())
            if (updated.isEmpty()) bySets.remove(exerciseName) else bySets[exerciseName] = updated

            if (bySets.isEmpty()) {
                // Une séance sans série n'est pas une séance : la laisser en base ferait compter
                // la journée comme tenue par le moteur d'escalade.
                workouts.delete(today)
                return@launch
            }

            // L'ordre du plan fait foi, pas l'ordre de saisie : on peut commencer par le
            // troisième exercice si la machine est prise.
            val ordered = state.value.exercises
                .mapNotNull { row -> bySets[row.name]?.let { ExerciseLog(row.name, it) } }

            workouts.save(WorkoutSession(today, DayTemplate(label), ordered))
        }
    }

    private suspend fun buildState(logged: WorkoutSession?, chosen: String?): SessionUiState {
        val plannedDays = plans.workoutDays()
        val plannedDay = plannedDays.firstOrNull { today.dayOfWeek in it.daysOfWeek }
            // Deux absences différentes, deux messages différents (DESIGN.md §8) : pas de plan
            // du tout, ou un plan qui ne prévoit rien aujourd'hui.
            ?: return SessionUiState(hasPlan = plannedDays.isNotEmpty(), loaded = true)

        val availableLoads = plans.availableLoadsKg()

        val rows = plannedDay.exercises.map { planned ->
            val sets = logged?.exercises?.firstOrNull { it.name == planned.name }?.sets.orEmpty()
            ExerciseRow(
                name = planned.name,
                prescribedSets = planned.prescribedSets,
                repRange = planned.repRange,
                sets = sets.mapIndexed { index, set ->
                    SetRow(index, set.reps, set.weightKg, set.cleanTechnique)
                },
                suggestedLoadKg = DoubleProgression.suggestedLoadKg(
                    previousSets = previousSets(planned.name),
                    repRange = planned.repRange,
                    prescribedSets = planned.prescribedSets,
                    availableLoadsKg = availableLoads,
                ),
                earnedNextLoadKg = earnedNextLoad(
                    sets = sets,
                    repRange = planned.repRange,
                    prescribedSets = planned.prescribedSets,
                    availableLoads = availableLoads,
                ),
            )
        }

        return SessionUiState(
            label = plannedDay.label,
            exercises = rows,
            // Sans choix explicite, l'écran ouvre sur le premier exercice qui reste à faire.
            selected = chosen ?: rows.firstOrNull { !it.done }?.name ?: rows.firstOrNull()?.name,
            loaded = true,
        )
    }

    /** Séries de la dernière séance où cet exercice a été travaillé, hors séance du jour. */
    private suspend fun previousSets(exerciseName: String): List<SetLog> =
        workouts.historyFor(exerciseName, today.minusDays(LOOKBACK_DAYS))
            .filter { it.date < today }
            .maxByOrNull { it.date }
            ?.exercises
            ?.firstOrNull { it.name == exerciseName }
            ?.sets
            .orEmpty()

    private fun earnedNextLoad(
        sets: List<SetLog>,
        repRange: RepRange,
        prescribedSets: Int,
        availableLoads: List<Float>,
    ): Float? {
        if (!DoubleProgression.shouldIncreaseLoad(sets, repRange, prescribedSets)) return null
        return DoubleProgression.nextLoadKg(sets.minOf { it.weightKg }, availableLoads)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * Profondeur d'historique pour la charge proposée. Huit semaines, la durée du programme :
         * au-delà, la dernière charge tenue ne dit plus grand-chose de ce qu'on peut faire
         * aujourd'hui.
         */
        const val LOOKBACK_DAYS = 56L
    }
}
