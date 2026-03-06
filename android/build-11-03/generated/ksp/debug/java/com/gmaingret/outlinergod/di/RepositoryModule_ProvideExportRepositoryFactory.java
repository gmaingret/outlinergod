package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.repository.ExportRepository;
import com.gmaingret.outlinergod.repository.impl.ExportRepositoryImpl;
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
public final class RepositoryModule_ProvideExportRepositoryFactory implements Factory<ExportRepository> {
  private final Provider<ExportRepositoryImpl> implProvider;

  public RepositoryModule_ProvideExportRepositoryFactory(
      Provider<ExportRepositoryImpl> implProvider) {
    this.implProvider = implProvider;
  }

  @Override
  public ExportRepository get() {
    return provideExportRepository(implProvider.get());
  }

  public static RepositoryModule_ProvideExportRepositoryFactory create(
      Provider<ExportRepositoryImpl> implProvider) {
    return new RepositoryModule_ProvideExportRepositoryFactory(implProvider);
  }

  public static ExportRepository provideExportRepository(ExportRepositoryImpl impl) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.provideExportRepository(impl));
  }
}
