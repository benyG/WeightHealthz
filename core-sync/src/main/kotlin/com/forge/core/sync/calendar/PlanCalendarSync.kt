package com.forge.core.sync.calendar

import com.forge.domain.repository.PlanRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée de l'onboarding (SPEC.md §5.1) : lit le programme importé et le pose dans
 * l'agenda.
 *
 * Rejouable sans dommage — la synchronisation compte ce qui existe déjà au lieu de le recréer,
 * donc relancer l'onboarding ne remplit pas l'agenda de doublons.
 */
@Singleton
class PlanCalendarSync @Inject constructor(
    private val plans: PlanRepository,
    private val calendar: CalendarSync,
) {

    suspend fun syncPlan(from: LocalDate = LocalDate.now()): CalendarSync.Result {
        val specs = PlanCalendarEvents.build(
            workoutDays = plans.workoutDays(),
            meals = plans.meals(),
            from = from,
        )
        return calendar.sync(specs)
    }
}
