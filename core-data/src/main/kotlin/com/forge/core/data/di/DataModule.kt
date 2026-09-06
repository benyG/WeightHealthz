package com.forge.core.data.di

import android.content.Context
import androidx.room.Room
import com.forge.core.data.db.AdherenceDao
import com.forge.core.data.db.ForgeDatabase
import com.forge.core.data.db.MealDao
import com.forge.core.data.db.PlanDao
import com.forge.core.data.db.WeeklyAnalysisDao
import com.forge.core.data.db.WeightDao
import com.forge.core.data.db.WorkoutDao
import com.forge.core.data.repository.RoomAdherenceRepository
import com.forge.core.data.repository.RoomMealRepository
import com.forge.core.data.repository.RoomPlanRepository
import com.forge.core.data.repository.RoomWeeklyAnalysisRepository
import com.forge.core.data.repository.RoomWeightRepository
import com.forge.core.data.repository.RoomWorkoutRepository
import com.forge.domain.repository.AdherenceRepository
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WeeklyAnalysisRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ForgeDatabase =
        Room.databaseBuilder(context, ForgeDatabase::class.java, ForgeDatabase.NAME).build()

    @Provides
    fun weightDao(database: ForgeDatabase): WeightDao = database.weightDao()

    @Provides
    fun mealDao(database: ForgeDatabase): MealDao = database.mealDao()

    @Provides
    fun workoutDao(database: ForgeDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun adherenceDao(database: ForgeDatabase): AdherenceDao = database.adherenceDao()

    @Provides
    fun weeklyAnalysisDao(database: ForgeDatabase): WeeklyAnalysisDao = database.weeklyAnalysisDao()

    @Provides
    fun planDao(database: ForgeDatabase): PlanDao = database.planDao()

    /**
     * `ignoreUnknownKeys` : le fichier de plan porte un champ `note` explicatif, et un plan réel
     * pourra en porter d'autres sans qu'une clé de plus fasse échouer l'import.
     */
    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun weightRepository(impl: RoomWeightRepository): WeightRepository

    @Binds
    abstract fun mealRepository(impl: RoomMealRepository): MealRepository

    @Binds
    abstract fun workoutRepository(impl: RoomWorkoutRepository): WorkoutRepository

    @Binds
    abstract fun adherenceRepository(impl: RoomAdherenceRepository): AdherenceRepository

    @Binds
    abstract fun weeklyAnalysisRepository(impl: RoomWeeklyAnalysisRepository): WeeklyAnalysisRepository

    @Binds
    abstract fun planRepository(impl: RoomPlanRepository): PlanRepository
}
