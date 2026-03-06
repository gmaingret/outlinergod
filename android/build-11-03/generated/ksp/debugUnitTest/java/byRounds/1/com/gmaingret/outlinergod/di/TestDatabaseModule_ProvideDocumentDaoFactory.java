package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.db.AppDatabase;
import com.gmaingret.outlinergod.db.dao.DocumentDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class TestDatabaseModule_ProvideDocumentDaoFactory implements Factory<DocumentDao> {
  private final Provider<AppDatabase> dbProvider;

  public TestDatabaseModule_ProvideDocumentDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DocumentDao get() {
    return provideDocumentDao(dbProvider.get());
  }

  public static TestDatabaseModule_ProvideDocumentDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new TestDatabaseModule_ProvideDocumentDaoFactory(dbProvider);
  }

  public static DocumentDao provideDocumentDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(TestDatabaseModule.INSTANCE.provideDocumentDao(db));
  }
}
