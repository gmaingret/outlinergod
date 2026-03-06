package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.repository.SearchRepository;
import com.gmaingret.outlinergod.repository.impl.SearchRepositoryImpl;
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
public final class RepositoryModule_ProvideSearchRepositoryFactory implements Factory<SearchRepository> {
  private final Provider<SearchRepositoryImpl> implProvider;

  public RepositoryModule_ProvideSearchRepositoryFactory(
      Provider<SearchRepositoryImpl> implProvider) {
    this.implProvider = implProvider;
  }

  @Override
  public SearchRepository get() {
    return provideSearchRepository(implProvider.get());
  }

  public static RepositoryModule_ProvideSearchRepositoryFactory create(
      Provider<SearchRepositoryImpl> implProvider) {
    return new RepositoryModule_ProvideSearchRepositoryFactory(implProvider);
  }

  public static SearchRepository provideSearchRepository(SearchRepositoryImpl impl) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.provideSearchRepository(impl));
  }
}
