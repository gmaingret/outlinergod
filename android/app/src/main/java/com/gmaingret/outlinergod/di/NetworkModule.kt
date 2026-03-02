package com.gmaingret.outlinergod.di

import com.gmaingret.outlinergod.BuildConfig
import com.gmaingret.outlinergod.network.KtorClientFactory
import com.gmaingret.outlinergod.repository.AuthRepository
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(authRepo: Lazy<AuthRepository>): HttpClient = KtorClientFactory.create(
        tokenProvider = { authRepo.get().getAccessToken().first() },
        tokenRefresher = { authRepo.get().refreshToken().getOrNull()?.token }
    )

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.BASE_URL
}
