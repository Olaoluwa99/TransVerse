package com.easit.aiscanner.di

import android.content.Context
import android.content.SharedPreferences
import com.easit.aiscanner.data.Constants
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
    fun getAppDatabase(context: Context): ScanDatabase{
        return ScanDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideScanDao(appDb: ScanDatabase): ScanDao{
        return appDb.getScanDao()
    }

    @AppTheme
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_THEME, Context.MODE_PRIVATE)
    }

    @AppFontSize
    @Provides
    @Singleton
    fun provideFontSizeSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_FONT_SIZE, Context.MODE_PRIVATE)
    }

    @AppSelectedLanguage
    @Provides
    @Singleton
    fun provideLanguageSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_LANGUAGE, Context.MODE_PRIVATE)
    }

    @AppBubbleSize
    @Provides
    @Singleton
    fun provideBubbleSizeSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_BUBBLE_SIZE, Context.MODE_PRIVATE)
    }

    @AppReadingSpeed
    @Provides
    @Singleton
    fun provideReadingSpeedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_READING_SPEED, Context.MODE_PRIVATE)
    }

    @AppSpeechPitch
    @Provides
    @Singleton
    fun provideSpeechPitchSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_SPEECH_PITCH, Context.MODE_PRIVATE)
    }

    @AppIncognitoMode
    @Provides
    @Singleton
    fun provideIncognitoModeSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences{
        return context.getSharedPreferences(Constants.APP_INCOGNITO_MODE, Context.MODE_PRIVATE)
    }

}
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppTheme

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppFontSize

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppSelectedLanguage

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppBubbleSize

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppReadingSpeed

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppSpeechPitch

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppIncognitoMode
