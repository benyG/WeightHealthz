package com.forge.work

import com.forge.core.sync.relay.EscalationRelay
import com.forge.core.sync.relay.VoiceRelay
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.EscalationLevel
import com.forge.domain.model.MealCheck
import com.forge.domain.model.MealSlot
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import com.forge.domain.model.WorkoutSession
import com.forge.domain.repository.AdherenceRepository
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de la porte de sortie du MVP (IMPLEMENTATION_PLAN.md §9, SPEC.md §10).
 *
 * Il vérifie que les canaux convergent : un passage en `CRITIQUE` poste la notification Android
 * **et** part vers l'enceinte dans le même événement, avec le même message — pas dans deux
 * traitements séparés qu'on pourrait livrer l'un sans l'autre.
 *
 * Le relais concret (webhook Notify-My-Alexa ou IFTTT) n'est pas encore choisi ; le test porte
 * sur le point de convergence, que le fournisseur ne changera pas.
 */
class EscalationConvergenceTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    // --- Doublures ---

    private class FakeWeights(var entries: List<WeightEntry> = emptyList()) : WeightRepository {
        override fun observeAll(): Flow<List<WeightEntry>> = flowOf(entries)
        override suspend fun entriesBetween(from: LocalDate, to: LocalDate) =
            entries.filter { it.date >= from && it.date <= to }
        override suspend fun record(entry: WeightEntry) {
            entries = entries + entry
        }
    }

    private class FakeMeals(var checks: List<MealCheck> = emptyList()) : MealRepository {
        override fun observeDay(date: LocalDate): Flow<List<MealCheck>> =
            flowOf(checks.filter { it.date == date })
        override suspend fun setChecked(date: LocalDate, slot: MealSlot, done: Boolean) {
            checks = checks + MealCheck(date, slot, done)
        }
    }

    private class FakeWorkouts(var session: WorkoutSession? = null) : WorkoutRepository {
        override fun observeSession(date: LocalDate): Flow<WorkoutSession?> = flowOf(session)
        override suspend fun save(session: WorkoutSession) { this.session = session }
        override suspend fun historyFor(exerciseName: String, since: LocalDate) = emptyList<WorkoutSession>()
        override suspend fun sessionsBetween(from: LocalDate, to: LocalDate) = emptyList<WorkoutSession>()
    }

    private class FakeAdherence : AdherenceRepository {
        private val state = MutableStateFlow(AdherenceState.START)
        override fun observeState(): Flow<AdherenceState> = state
        override suspend fun update(state: AdherenceState) { this.state.value = state }
    }

    private class RecordingNotifier : EscalationNotifier {
        val posted = mutableListOf<Pair<EscalationLevel, String>>()
        override fun notifyEscalation(level: EscalationLevel, message: String) {
            posted += level to message
        }
    }

    private class RecordingRelay(private val configured: Boolean = true) : VoiceRelay {
        val announced = mutableListOf<String>()
        override fun isConfigured() = configured
        override suspend fun announce(message: String): VoiceRelay.RelayResult {
            announced += message
            return if (configured) VoiceRelay.RelayResult.Delivered else VoiceRelay.RelayResult.NotConfigured
        }
    }

    private fun runner(
        weights: FakeWeights = FakeWeights(),
        meals: FakeMeals = FakeMeals(),
        workouts: FakeWorkouts = FakeWorkouts(),
        adherence: AdherenceRepository = FakeAdherence(),
        notifier: EscalationNotifier = RecordingNotifier(),
        relay: VoiceRelay = RecordingRelay(),
    ) = EscalationRunner(weights, meals, workouts, adherence, notifier, EscalationRelay(relay))

    // --- Le test qui garde le MVP ---

    @Test
    fun `trois jours sans action postent la notification ET partent vers l enceinte`() = runTest {
        val notifier = RecordingNotifier()
        val relay = RecordingRelay()
        val adherence = FakeAdherence()
        val runner = runner(adherence = adherence, notifier = notifier, relay = relay)

        runner.closeDay(today.minusDays(2))
        runner.closeDay(today.minusDays(1))
        val state = runner.closeDay(today)

        assertEquals(EscalationLevel.CRITIQUE, state.escalationLevel)

        // (a) La notification critique est postée.
        val criticalNotification = notifier.posted.last()
        assertEquals(EscalationLevel.CRITIQUE, criticalNotification.first)

        // (b) Le relais vocal a reçu le même message, dans le même événement.
        assertEquals(criticalNotification.second, relay.announced.last())
    }

    @Test
    fun `un premier retard reste sur le telephone`() = runTest {
        val notifier = RecordingNotifier()
        val relay = RecordingRelay()

        runner(notifier = notifier, relay = relay).closeDay(today)

        assertEquals(EscalationLevel.RETARD_1, notifier.posted.single().first)
        // CLAUDE.md : seuls RETARD_2 et CRITIQUE méritent la voix.
        assertTrue("L'enceinte ne doit pas parler pour un premier retard", relay.announced.isEmpty())
    }

    @Test
    fun `le deuxieme jour fait deja parler l enceinte`() = runTest {
        val notifier = RecordingNotifier()
        val relay = RecordingRelay()
        val runner = runner(notifier = notifier, relay = relay)

        runner.closeDay(today.minusDays(1))
        runner.closeDay(today)

        assertEquals(EscalationLevel.RETARD_2, notifier.posted.last().first)
        assertEquals(1, relay.announced.size)
    }

    @Test
    fun `une action valide dans la journee eteint tout`() = runTest {
        val notifier = RecordingNotifier()
        val relay = RecordingRelay()
        val weights = FakeWeights(listOf(WeightEntry(today, 82.4f, WeightSource.MANUAL)))

        val state = runner(weights = weights, notifier = notifier, relay = relay).closeDay(today)

        assertEquals(EscalationLevel.A_JOUR, state.escalationLevel)
        assertTrue(notifier.posted.isEmpty())
        assertTrue(relay.announced.isEmpty())
    }

    @Test
    fun `un repas coche suffit a tenir la journee`() = runTest {
        val meals = FakeMeals(listOf(MealCheck(today, MealSlot.DEJEUNER, done = true)))

        val state = runner(meals = meals).closeDay(today)

        assertEquals(EscalationLevel.A_JOUR, state.escalationLevel)
        assertEquals(1, state.streakDays)
    }

    @Test
    fun `un relais absent n empeche pas la notification`() = runTest {
        val notifier = RecordingNotifier()
        // Cas réel d'aujourd'hui : le fournisseur de relais n'est pas encore choisi.
        val relay = RecordingRelay(configured = false)
        val runner = runner(notifier = notifier, relay = relay)

        runner.closeDay(today.minusDays(2))
        runner.closeDay(today.minusDays(1))
        runner.closeDay(today)

        // L'app reste utilisable sans enceinte : c'est un canal en moins, pas une panne.
        assertEquals(EscalationLevel.CRITIQUE, notifier.posted.last().first)
        assertNotNull(notifier.posted.last().second)
    }

    @Test
    fun `le message nomme le geste le plus court pour repartir`() = runTest {
        val notifier = RecordingNotifier()
        val relay = RecordingRelay()

        val runner = runner(notifier = notifier, relay = relay)
        runner.closeDay(today.minusDays(1))
        runner.closeDay(today)

        // Une journée qui escalade est une journée où rien n'a été fait : plutôt qu'une liste de
        // reproches, le message nomme la pesée, le geste qui suffit à repartir.
        assertTrue(notifier.posted.last().second.startsWith("Pesée"))
        assertEquals(notifier.posted.last().second, relay.announced.last())
    }
}
