package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.repository.SyncRepository;
import com.gmaingret.outlinergod.repository.impl.SyncRepositoryImpl;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class RepositoryModule_ProvideSyncRepositoryFactory implements Factory<SyncRepository> {
  private final Provider<SyncRepositoryImpl> implProvider;

  public RepositoryModule_ProvideSyncRepositoryFactory(Provider<SyncRepositoryImpl> implProvider) {
    this.implProvider = implProvider;
  }

  @Override
  public SyncRepository get() {
    return provideSyncRepository(implProvider.get());
  }

  public static RepositoryModule_ProvideSyncRepositoryFactory create(
      Provider<SyncRepositoryImpl> implProvider) {
    return new RepositoryModule_ProvideSyncRepositoryFactory(implProvider);
  }

  public static SyncRepository provideSyncRepository(SyncRepositoryImpl impl) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.provideSyncRepository(impl));
  }
}
