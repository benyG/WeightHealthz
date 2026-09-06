package com.forge.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.domain.model.MealSlot
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MealRow(
    val slot: MealSlot,
    val label: String,
    val description: String,
    val timeOfDay: LocalTime?,
    val done: Boolean,
)

data class MealsUiState(
    val rows: List<MealRow> = emptyList(),
    val variationOfTheDay: String? = null,
) {
    val remaining: Int get() = rows.count { !it.done }
}

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val meals: MealRepository,
    private val plans: PlanRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MealsUiState> = meals.observeDay(today)
        .mapLatest { checks ->
            val checkedSlots = checks.filter { it.done }.map { it.slot }.toSet()
            MealsUiState(
                rows = plans.meals().map { planned ->
                    MealRow(
                        slot = planned.slot,
                        label = planned.label,
                        description = planned.description,
                        timeOfDay = planned.timeOfDay,
                        done = planned.slot in checkedSlots,
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealsUiState())

    fun toggle(row: MealRow, done: Boolean) {
        viewModelScope.launch {
            meals.setChecked(today, row.slot, done)
        }
    }
}
