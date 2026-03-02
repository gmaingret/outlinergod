package com.gmaingret.outlinergod.di

import com.gmaingret.outlinergod.sync.HlcClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideHlcClock(): HlcClock =
        throw NotImplementedError("Wired in P3-9")
}
