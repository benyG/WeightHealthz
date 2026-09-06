package com.forge.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forge.core.data.db.ForgeDatabase
import com.forge.core.data.repository.RoomAdherenceRepository
import com.forge.core.data.repository.RoomMealRepository
import com.forge.core.data.repository.RoomWeightRepository
import com.forge.core.data.repository.RoomWorkoutRepository
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.DayTemplate
import com.forge.domain.model.EscalationLevel
import com.forge.domain.model.ExerciseLog
import com.forge.domain.model.MealSlot
import com.forge.domain.model.SetLog
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import com.forge.domain.model.WorkoutSession
import com.forge.domain.rule.WeightTrend
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositoriesTest {

    private lateinit var database: ForgeDatabase
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ForgeDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `une pesee traverse le repository jusqu a la relecture`() = runTest {
        val repository = RoomWeightRepository(database.weightDao())
        val entry = WeightEntry(today, 82.4f, WeightSource.MANUAL)

        repository.record(entry)

        assertEquals(listOf(entry), repository.observeAll().first())
        assertEquals(listOf(entry), repository.entriesBetween(today.minusDays(6), today))
    }

    @Test
    fun `resynchroniser la meme pesee ne cree pas de doublon`() = runTest {
        val repository = RoomWeightRepository(database.weightDao())
        val fromHealthConnect = WeightEntry(today, 82.4f, WeightSource.HEALTH_CONNECT)

        // Deux lectures Health Connect de la même journée : le second passage corrige, il
        // n'empile pas. C'est ce qui rend le rappel de pesée rejouable sans effet de bord.
        repository.record(fromHealthConnect)
        repository.record(fromHealthConnect.copy(kg = 82.6f))

        val stored = repository.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals(82.6f, stored.single().kg, 1e-6f)
    }

    @Test
    fun `les deux sources coexistent le meme jour et la moyenne les combine`() = runTest {
        val repository = RoomWeightRepository(database.weightDao())
        repository.record(WeightEntry(today, 82f, WeightSource.MANUAL))
        repository.record(WeightEntry(today, 84f, WeightSource.HEALTH_CONNECT))

        val stored = repository.observeAll().first()

        assertEquals(2, stored.size)
        // La règle du domaine moyenne les pesées d'une même journée avant de lisser.
        assertEquals(83.0, WeightTrend.movingAverageKg(stored, today)!!, 1e-9)
    }

    @Test
    fun `cocher puis decocher un repas conserve une seule ligne`() = runTest {
        val repository = RoomMealRepository(database.mealDao())

        repository.setChecked(today, MealSlot.DEJEUNER, done = true)
        repository.setChecked(today, MealSlot.DEJEUNER, done = false)

        val checks = repository.observeDay(today).first()
        assertEquals(1, checks.size)
        assertEquals(false, checks.single().done)
    }

    @Test
    fun `une seance fait l aller-retour en conservant l ordre`() = runTest {
        val repository = RoomWorkoutRepository(database.workoutDao())
        val session = WorkoutSession(
            date = today,
            dayTemplate = DayTemplate("Bas du corps"),
            exercises = listOf(
                ExerciseLog(
                    name = "Squat gobelet",
                    sets = listOf(
                        SetLog(reps = 12, weightKg = 16f, cleanTechnique = true),
                        SetLog(reps = 10, weightKg = 16f, cleanTechnique = false),
                    ),
                ),
                ExerciseLog(
                    name = "Fente avant",
                    sets = listOf(SetLog(reps = 10, weightKg = 12f, cleanTechnique = true)),
                ),
            ),
        )

        repository.save(session)

        // L'ordre compte : l'écran de montre annonce "exercice 3 sur 6" et enchaîne les séries.
        assertEquals(session, repository.observeSession(today).first())
    }

    @Test
    fun `resauvegarder une seance corrigee ne duplique pas les series`() = runTest {
        val repository = RoomWorkoutRepository(database.workoutDao())
        val session = WorkoutSession(
            date = today,
            dayTemplate = DayTemplate("Haut du corps"),
            exercises = listOf(
                ExerciseLog("Développé couché haltères", listOf(SetLog(10, 20f, true))),
            ),
        )
        repository.save(session)

        val corrected = session.copy(
            exercises = listOf(
                ExerciseLog("Développé couché haltères", listOf(SetLog(12, 20f, true))),
            ),
        )
        repository.save(corrected)

        val stored = repository.observeSession(today).first()
        assertNotNull(stored)
        assertEquals(1, stored!!.exercises.single().sets.size)
        assertEquals(12, stored.exercises.single().sets.single().reps)
    }

    @Test
    fun `effacer la seance ne laisse ni exercice ni serie derriere elle`() = runTest {
        val repository = RoomWorkoutRepository(database.workoutDao())
        repository.save(
            WorkoutSession(
                today,
                DayTemplate("Bas du corps"),
                listOf(ExerciseLog("Squat gobelet", listOf(SetLog(12, 16f, true)))),
            ),
        )

        repository.delete(today)

        // Nul, et pas une séance vide : une séance vide ferait compter la journée comme tenue.
        assertNull(repository.observeSession(today).first())
        assertEquals(emptyList<WorkoutSession>(), repository.historyFor("Squat gobelet", today.minusDays(7)))
    }

    @Test
    fun `l historique ne retient que les seances contenant l exercice`() = runTest {
        val repository = RoomWorkoutRepository(database.workoutDao())
        repository.save(
            WorkoutSession(
                today.minusDays(7),
                DayTemplate("Bas du corps"),
                listOf(ExerciseLog("Squat gobelet", listOf(SetLog(12, 16f, true)))),
            ),
        )
        repository.save(
            WorkoutSession(
                today.minusDays(3),
                DayTemplate("Haut du corps"),
                listOf(ExerciseLog("Rowing haltère", listOf(SetLog(10, 18f, true)))),
            ),
        )

        val history = repository.historyFor("Squat gobelet", since = today.minusDays(30))

        assertEquals(1, history.size)
        assertEquals(today.minusDays(7), history.single().date)
    }

    @Test
    fun `une base vierge rend l etat de depart du domaine`() = runTest {
        val repository = RoomAdherenceRepository(database.adherenceDao())

        assertEquals(AdherenceState.START, repository.observeState().first())
    }

    @Test
    fun `l etat d adherence se relit tel qu il a ete ecrit`() = runTest {
        val repository = RoomAdherenceRepository(database.adherenceDao())
        val state = AdherenceState(streakDays = 5, escalationLevel = EscalationLevel.RETARD_2)

        repository.update(state)
        repository.update(state.copy(streakDays = 6))

        val stored = repository.observeState().first()
        assertEquals(6, stored.streakDays)
        assertTrue(stored.escalationLevel.requiresVoiceRelay)
    }
}
