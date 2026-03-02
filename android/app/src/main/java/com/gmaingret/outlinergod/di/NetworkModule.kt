package com.gmaingret.outlinergod.di

import com.gmaingret.outlinergod.BuildConfig
import com.gmaingret.outlinergod.network.KtorClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = KtorClientFactory.create(
        tokenProvider = { null },      // replaced when AuthRepository is wired in P3-12
        tokenRefresher = { null }      // replaced when AuthRepository is wired in P3-12
    )

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.BASE_URL
}
