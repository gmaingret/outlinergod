package com.gmaingret.outlinergod.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Named("repositoryModulePlaceholder")
    fun provideNoOpBinding(): String = "placeholder"
}
