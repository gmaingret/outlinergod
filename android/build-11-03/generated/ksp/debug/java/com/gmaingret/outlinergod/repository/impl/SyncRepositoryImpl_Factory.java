package com.gmaingret.outlinergod.repository.impl;

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
public final class SyncRepositoryImpl_Factory implements Factory<SyncRepositoryImpl> {
  private final Provider<HttpClient> httpClientProvider;

  private final Provider<String> baseUrlProvider;

  public SyncRepositoryImpl_Factory(Provider<HttpClient> httpClientProvider,
      Provider<String> baseUrlProvider) {
    this.httpClientProvider = httpClientProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  @Override
  public SyncRepositoryImpl get() {
    return newInstance(httpClientProvider.get(), baseUrlProvider.get());
  }

  public static SyncRepositoryImpl_Factory create(Provider<HttpClient> httpClientProvider,
      Provider<String> baseUrlProvider) {
    return new SyncRepositoryImpl_Factory(httpClientProvider, baseUrlProvider);
  }

  public static SyncRepositoryImpl newInstance(HttpClient httpClient, String baseUrl) {
    return new SyncRepositoryImpl(httpClient, baseUrl);
  }
}
