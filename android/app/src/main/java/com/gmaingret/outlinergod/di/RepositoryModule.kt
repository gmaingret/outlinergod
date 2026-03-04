package com.gmaingret.outlinergod.di

import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.ExportRepository
import com.gmaingret.outlinergod.repository.SearchRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.repository.impl.AuthRepositoryImpl
import com.gmaingret.outlinergod.repository.impl.ExportRepositoryImpl
import com.gmaingret.outlinergod.repository.impl.SearchRepositoryImpl
import com.gmaingret.outlinergod.repository.impl.SyncRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(impl: AuthRepositoryImpl): AuthRepository = impl

    @Provides
    @Singleton
    fun provideSyncRepository(impl: SyncRepositoryImpl): SyncRepository = impl

    @Provides
    @Singleton
    fun provideExportRepository(impl: ExportRepositoryImpl): ExportRepository = impl

    @Provides
    @Singleton
    fun provideSearchRepository(impl: SearchRepositoryImpl): SearchRepository = impl
}
