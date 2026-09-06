package com.forge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.MealCheck
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.WeightEntry
import com.forge.domain.repository.AdherenceRepository
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.rule.ProgramProgress
import com.forge.domain.rule.ProgramWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * État de l'écran d'accueil. Un seul élément domine (DESIGN.md §5) : l'écart au poids cible ;
 * tout le reste est une ligne réglée secondaire.
 */
data class HomeUiState(
    val weekIndex: Int? = null,
    val totalWeeks: Int = 0,
    val cumulativeDeltaKg: Double? = null,
    val target: PlanTarget? = null,
    val todayWeightKg: Float? = null,
    val todaySession: PlannedWorkoutDay? = null,
    val mealsRemaining: Int = 0,
    val mealsTotal: Int = 0,
    val adherence: AdherenceState = AdherenceState.START,
    val hasPlan: Boolean = true,
) {
    /**
     * Vrai seulement quand la donnée mesurée confirme qu'on est dans la fourchette : c'est la
     * condition d'emploi de la couleur « mousse » (DESIGN.md §2).
     */
    val isOnTarget: Boolean
        get() = cumulativeDeltaKg != null && target?.contains(cumulativeDeltaKg.toFloat()) == true
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val weights: WeightRepository,
    private val meals: MealRepository,
    private val plans: PlanRepository,
    private val adherence: AdherenceRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = today
        .flatMapLatest { date ->
            combine(
                weights.observeAll(),
                meals.observeDay(date),
                adherence.observeState(),
            ) { entries, checks, adherenceState -> Triple(entries, checks, adherenceState) }
                .mapLatest { (entries, checks, adherenceState) ->
                    buildState(date, entries, checks, adherenceState)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** Recalcule sur la date du jour : l'app peut être rouverte le lendemain sans être relancée. */
    fun refresh() {
        today.value = LocalDate.now()
    }

    private suspend fun buildState(
        date: LocalDate,
        entries: List<WeightEntry>,
        checks: List<MealCheck>,
        adherenceState: AdherenceState,
    ): HomeUiState {
        val start = plans.programStartDate()
        val weekIndex = start?.let { ProgramWeek.indexFor(it, date) }
        val plannedMeals = plans.meals()
        val todayEntries = entries.filter { it.date == date }

        return HomeUiState(
            weekIndex = weekIndex,
            totalWeeks = plans.programWeekCount(),
            cumulativeDeltaKg = ProgramProgress.cumulativeDeltaKg(entries, date),
            target = weekIndex?.let { plans.targetForWeek(it) },
            // Plusieurs pesées le même jour : on affiche leur moyenne, comme la règle du domaine.
            todayWeightKg = todayEntries.takeIf { it.isNotEmpty() }?.map { it.kg }?.average()?.toFloat(),
            todaySession = plans.workoutDays().firstOrNull { date.dayOfWeek in it.daysOfWeek },
            mealsRemaining = (plannedMeals.size - checks.count { it.done }).coerceAtLeast(0),
            mealsTotal = plannedMeals.size,
            adherence = adherenceState,
            hasPlan = start != null,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
