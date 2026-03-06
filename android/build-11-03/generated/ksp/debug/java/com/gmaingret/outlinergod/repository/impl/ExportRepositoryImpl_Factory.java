package com.gmaingret.outlinergod.repository.impl;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata({
    "javax.inject.Named",
    "dagger.hilt.android.qualifiers.ApplicationContext"
})
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
public final class ExportRepositoryImpl_Factory implements Factory<ExportRepositoryImpl> {
  private final Provider<HttpClient> httpClientProvider;

  private final Provider<String> baseUrlProvider;

  private final Provider<Context> contextProvider;

  public ExportRepositoryImpl_Factory(Provider<HttpClient> httpClientProvider,
      Provider<String> baseUrlProvider, Provider<Context> contextProvider) {
    this.httpClientProvider = httpClientProvider;
    this.baseUrlProvider = baseUrlProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public ExportRepositoryImpl get() {
    return newInstance(httpClientProvider.get(), baseUrlProvider.get(), contextProvider.get());
  }

  public static ExportRepositoryImpl_Factory create(Provider<HttpClient> httpClientProvider,
      Provider<String> baseUrlProvider, Provider<Context> contextProvider) {
    return new ExportRepositoryImpl_Factory(httpClientProvider, baseUrlProvider, contextProvider);
  }

  public static ExportRepositoryImpl newInstance(HttpClient httpClient, String baseUrl,
      Context context) {
    return new ExportRepositoryImpl(httpClient, baseUrl, context);
  }
}
