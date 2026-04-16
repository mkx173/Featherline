package com.mkx.hrttracker.di

import android.content.Context
import androidx.room.Room
import com.mkx.hrttracker.data.local.DatabasePassphraseProvider
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideHrtTrackerDatabase(
        @ApplicationContext context: Context,
        databasePassphraseProvider: DatabasePassphraseProvider
    ): HrtTrackerDatabase {
        val openHelperFactory = SupportOpenHelperFactory(databasePassphraseProvider.getPassphrase())

        return Room.databaseBuilder(
            context,
            HrtTrackerDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(openHelperFactory)
            .build()
    }

    @Provides
    fun provideMedicationLogDao(database: HrtTrackerDatabase): MedicationLogDao {
        return database.medicationLogDao()
    }

    private const val DATABASE_NAME = "hrt_tracker.db"
}
