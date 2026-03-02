package com.gmaingret.outlinergod.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import javax.inject.Named
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object TestNetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { respondOk() }
        }
    }

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = "http://localhost:3000"
}
