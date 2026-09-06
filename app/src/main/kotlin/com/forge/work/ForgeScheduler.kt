package com.forge.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.forge.core.ai.work.WeeklyAnalysisWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programme les trois travaux périodiques de l'app.
 *
 * `KEEP` partout : relancer l'app ne redécale pas des rappels déjà planifiés. Un changement
 * d'horaire devra passer explicitement par `UPDATE`, quand l'écran de réglages existera.
 */
@Singleton
class ForgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleAll(
        weighInAt: LocalTime = DEFAULT_WEIGH_IN_TIME,
        dayCloseAt: LocalTime = DEFAULT_DAY_CLOSE_TIME,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            WeighInReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeighInReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(weighInAt, now).toMinutes(), TimeUnit.MINUTES)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            DailyEscalationWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyEscalationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(dayCloseAt, now).toMinutes(), TimeUnit.MINUTES)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            WeeklyAnalysisWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            WeeklyAnalysisWorker.request(),
        )
    }

    companion object {
        /** Heure du rappel de pesée, valeur par défaut de SPEC.md §5.2. */
        val DEFAULT_WEIGH_IN_TIME: LocalTime = LocalTime.of(7, 0)

        /**
         * Clôture de journée assez tard pour qu'une action du soir compte encore, assez tôt pour
         * que la notification arrive avant le coucher.
         */
        val DEFAULT_DAY_CLOSE_TIME: LocalTime = LocalTime.of(22, 0)

        /** Délai jusqu'à la prochaine occurrence de [time] — aujourd'hui si elle est à venir. */
        internal fun delayUntil(time: LocalTime, now: LocalDateTime): Duration {
            val todayAt = LocalDate.from(now).atTime(time)
            val next = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
