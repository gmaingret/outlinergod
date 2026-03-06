package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.repository.AuthRepository;
import com.gmaingret.outlinergod.repository.impl.AuthRepositoryImpl;
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
public final class RepositoryModule_ProvideAuthRepositoryFactory implements Factory<AuthRepository> {
  private final Provider<AuthRepositoryImpl> implProvider;

  public RepositoryModule_ProvideAuthRepositoryFactory(Provider<AuthRepositoryImpl> implProvider) {
    this.implProvider = implProvider;
  }

  @Override
  public AuthRepository get() {
    return provideAuthRepository(implProvider.get());
  }

  public static RepositoryModule_ProvideAuthRepositoryFactory create(
      Provider<AuthRepositoryImpl> implProvider) {
    return new RepositoryModule_ProvideAuthRepositoryFactory(implProvider);
  }

  public static AuthRepository provideAuthRepository(AuthRepositoryImpl impl) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.provideAuthRepository(impl));
  }
}
