package com.forge.core.data.plan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forge.core.data.db.ForgeDatabase
import com.forge.core.data.db.PlanMetadataEntity
import com.forge.core.data.repository.RoomPlanRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
class PlanImporterTest {

    private lateinit var database: ForgeDatabase
    private lateinit var importer: PlanImporter
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ForgeDatabase::class.java,
        ).allowMainThreadQueries().build()
        importer = PlanImporter(
            context = ApplicationProvider.getApplicationContext(),
            planDao = database.planDao(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `le premier lancement importe le plan`() = runTest {
        val outcome = importer.importFrom(planJson(version = 1), today = today)

        assertEquals(PlanImporter.Outcome.Imported(1), outcome)
        assertEquals(1, database.planDao().allTargets().size)
        assertEquals(1, database.planDao().workoutDays().size)
        assertEquals(1, database.planDao().meals().size)
    }

    @Test
    fun `relancer l app avec le meme plan ne reimporte rien`() = runTest {
        importer.importFrom(planJson(version = 1), today = today)
        val outcome = importer.importFrom(planJson(version = 1), today = today)

        assertEquals(PlanImporter.Outcome.AlreadyImported(1), outcome)
        // L'idempotence se vérifie sur le contenu, pas seulement sur le verdict.
        assertEquals(1, database.planDao().allTargets().size)
        assertEquals(1, database.planDao().workoutDays().single().exercises.size)
    }

    @Test
    fun `une nouvelle version remplace l ancien plan sans laisser de residu`() = runTest {
        importer.importFrom(planJson(version = 1, weekIndex = 1), today = today)
        val outcome = importer.importFrom(planJson(version = 2, weekIndex = 2), today = today)

        assertEquals(PlanImporter.Outcome.Replaced(from = 1, to = 2), outcome)
        val targets = database.planDao().allTargets()
        assertEquals(1, targets.size)
        assertEquals(2, targets.single().weekIndex)
    }

    @Test
    fun `un creneau de repas inconnu fait echouer l import`() = runTest {
        val invalid = planJson(version = 1).replace("\"PETIT_DEJEUNER\"", "\"BRUNCH\"")

        val error = runCatching { importer.importFrom(invalid, today = today) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("BRUNCH"))
        // Rien n'a été écrit : un plan invalide ne laisse pas la base à moitié remplie.
        assertNull(database.planDao().metadata(PlanMetadataEntity.SINGLETON_ID))
    }

    @Test
    fun `une fourchette de reps inversee fait echouer l import`() = runTest {
        val invalid = planJson(version = 1).replace("\"repMin\": 8", "\"repMin\": 14")

        val error = runCatching { importer.importFrom(invalid, today = today) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertNull(database.planDao().metadata(PlanMetadataEntity.SINGLETON_ID))
    }

    @Test
    fun `le plan livre dans les assets est valide et importable`() = runTest {
        // Ce test garde le fichier réellement embarqué : une faute de frappe dans plan.json
        // casse le build au lieu de se découvrir au premier lancement sur le téléphone.
        val outcome = importer.importIfNeeded(today = today)

        assertTrue(outcome is PlanImporter.Outcome.Imported)
        assertTrue(database.planDao().allTargets().isNotEmpty())
        assertEquals(6, database.planDao().meals().size)
        assertNotNull(RoomPlanRepository(database.planDao()).targetForWeek(4))
    }

    @Test
    fun `la cible hebdomadaire remonte au domaine`() = runTest {
        importer.importFrom(planJson(version = 1, weekIndex = 4), today = today)

        val target = RoomPlanRepository(database.planDao()).targetForWeek(4)

        assertNotNull(target)
        assertTrue(target!!.contains(1.5f))
        assertEquals(false, target.contains(3f))
    }

    private fun planJson(version: Int, weekIndex: Int = 1): String =
        """
        {
          "note": "fixture de test",
          "version": $version,
          "availableLoadsKg": [12, 16, 20],
          "weeklyTargets": [
            { "weekIndex": $weekIndex, "targetDeltaKgMin": 1.2, "targetDeltaKgMax": 2.0 }
          ],
          "workoutDays": [
            {
              "id": "bas-du-corps",
              "label": "Bas du corps",
              "exercises": [
                { "name": "Squat gobelet", "prescribedSets": 4, "repMin": 8, "repMax": 12 }
              ]
            }
          ],
          "meals": [
            { "slot": "PETIT_DEJEUNER", "label": "Petit-déjeuner", "description": "" }
          ]
        }
        """.trimIndent()
}
