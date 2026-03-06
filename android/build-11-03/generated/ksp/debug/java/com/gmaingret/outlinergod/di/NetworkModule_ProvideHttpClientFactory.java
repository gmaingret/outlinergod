package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.repository.AuthRepository;
import dagger.Lazy;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
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
public final class NetworkModule_ProvideHttpClientFactory implements Factory<HttpClient> {
  private final Provider<AuthRepository> authRepoProvider;

  public NetworkModule_ProvideHttpClientFactory(Provider<AuthRepository> authRepoProvider) {
    this.authRepoProvider = authRepoProvider;
  }

  @Override
  public HttpClient get() {
    return provideHttpClient(DoubleCheck.lazy(authRepoProvider));
  }

  public static NetworkModule_ProvideHttpClientFactory create(
      Provider<AuthRepository> authRepoProvider) {
    return new NetworkModule_ProvideHttpClientFactory(authRepoProvider);
  }

  public static HttpClient provideHttpClient(Lazy<AuthRepository> authRepo) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideHttpClient(authRepo));
  }
}
