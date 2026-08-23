package com.easit.aiscanner.di

import android.content.Context
import android.content.SharedPreferences
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanDao
import com.easit.aiscanner.database.ScanDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanDatabase =
        ScanDatabase.getDatabase(context)

    @Provides
    fun provideScanDao(database: ScanDatabase): ScanDao = database.getScanDao()

    @Provides
    @Singleton
    @AppPreferences
    fun providePreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(Preference.FILE_NAME, Context.MODE_PRIVATE)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPreferences
