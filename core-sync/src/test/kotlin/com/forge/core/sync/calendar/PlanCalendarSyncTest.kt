package com.forge.core.sync.calendar

import com.forge.domain.model.MealSlot
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.PlannedExercise
import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.RepRange
import com.forge.domain.repository.PlanRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCalendarSyncTest {

    private val from: LocalDate = LocalDate.of(2026, 3, 15)

    private class FakePlan(
        private val days: List<PlannedWorkoutDay> = emptyList(),
        private val mealList: List<PlannedMeal> = emptyList(),
    ) : PlanRepository {
        override suspend fun targetForWeek(weekIndex: Int): PlanTarget? = null
        override suspend fun plannedSessionsPerWeek() = days.size
        override suspend fun programStartDate(): LocalDate? = null
        override suspend fun workoutDays() = days
        override suspend fun meals() = mealList
        override suspend fun programWeekCount() = 0
    }

    private class RecordingCalendar(
        private val result: CalendarSync.Result = CalendarSync.Result.Synced(0, 0),
    ) : CalendarSync {
        var received: List<CalendarEventSpec> = emptyList()
        override suspend fun sync(specs: List<CalendarEventSpec>, zone: ZoneId): CalendarSync.Result {
            received = specs
            return result
        }
    }

    @Test
    fun `le programme importe devient des evenements`() = runTest {
        val calendar = RecordingCalendar()
        val plans = FakePlan(
            days = listOf(
                PlannedWorkoutDay(
                    id = "bas-du-corps",
                    label = "Bas du corps",
                    exercises = listOf(PlannedExercise("Squat gobelet", 4, RepRange(8, 12))),
                    daysOfWeek = setOf(DayOfWeek.MONDAY),
                    timeOfDay = LocalTime.of(18, 30),
                ),
            ),
            mealList = listOf(
                PlannedMeal(MealSlot.PETIT_DEJEUNER, "Petit-déjeuner", "", LocalTime.of(7, 0)),
            ),
        )

        PlanCalendarSync(plans, calendar).syncPlan(from)

        assertEquals(2, calendar.received.size)
        assertTrue(calendar.received.any { it.key.contains("workout") })
        assertTrue(calendar.received.any { it.key.contains("meal") })
    }

    @Test
    fun `un plan sans horaire ne pose rien dans l agenda`() = runTest {
        val calendar = RecordingCalendar()
        val plans = FakePlan(
            days = listOf(PlannedWorkoutDay("bas-du-corps", "Bas du corps", emptyList())),
            mealList = listOf(PlannedMeal(MealSlot.DEJEUNER, "Déjeuner", "")),
        )

        PlanCalendarSync(plans, calendar).syncPlan(from)

        assertTrue(calendar.received.isEmpty())
    }

    @Test
    fun `sans permission le resultat le dit au lieu d echouer`() = runTest {
        val calendar = RecordingCalendar(result = CalendarSync.Result.PermissionMissing)

        val result = PlanCalendarSync(FakePlan(), calendar).syncPlan(from)

        // L'app reste utilisable sans agenda : c'est un canal en moins, pas une panne.
        assertEquals(CalendarSync.Result.PermissionMissing, result)
    }
}
