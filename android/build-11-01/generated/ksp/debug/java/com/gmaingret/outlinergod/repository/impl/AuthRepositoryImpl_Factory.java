package com.gmaingret.outlinergod.repository.impl;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata("javax.inject.Named")
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
public final class AuthRepositoryImpl_Factory implements Factory<AuthRepositoryImpl> {
  private final Provider<HttpClient> httpClientProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<String> baseUrlProvider;

  public AuthRepositoryImpl_Factory(Provider<HttpClient> httpClientProvider,
      Provider<DataStore<Preferences>> dataStoreProvider, Provider<String> baseUrlProvider) {
    this.httpClientProvider = httpClientProvider;
    this.dataStoreProvider = dataStoreProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  @Override
  public AuthRepositoryImpl get() {
    return newInstance(httpClientProvider.get(), dataStoreProvider.get(), baseUrlProvider.get());
  }

  public static AuthRepositoryImpl_Factory create(Provider<HttpClient> httpClientProvider,
      Provider<DataStore<Preferences>> dataStoreProvider, Provider<String> baseUrlProvider) {
    return new AuthRepositoryImpl_Factory(httpClientProvider, dataStoreProvider, baseUrlProvider);
  }

  public static AuthRepositoryImpl newInstance(HttpClient httpClient,
      DataStore<Preferences> dataStore, String baseUrl) {
    return new AuthRepositoryImpl(httpClient, dataStore, baseUrl);
  }
}
