package com.gmaingret.outlinergod.di;

import android.content.Context;
import com.gmaingret.outlinergod.db.AppDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class TestDatabaseModule_ProvideTestAppDatabaseFactory implements Factory<AppDatabase> {
  private final Provider<Context> contextProvider;

  public TestDatabaseModule_ProvideTestAppDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AppDatabase get() {
    return provideTestAppDatabase(contextProvider.get());
  }

  public static TestDatabaseModule_ProvideTestAppDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new TestDatabaseModule_ProvideTestAppDatabaseFactory(contextProvider);
  }

  public static AppDatabase provideTestAppDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(TestDatabaseModule.INSTANCE.provideTestAppDatabase(context));
  }
}
