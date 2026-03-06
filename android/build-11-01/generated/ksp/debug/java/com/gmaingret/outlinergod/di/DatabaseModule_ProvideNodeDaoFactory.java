package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.db.AppDatabase;
import com.gmaingret.outlinergod.db.dao.NodeDao;
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
public final class DatabaseModule_ProvideNodeDaoFactory implements Factory<NodeDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideNodeDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public NodeDao get() {
    return provideNodeDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideNodeDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideNodeDaoFactory(dbProvider);
  }

  public static NodeDao provideNodeDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideNodeDao(db));
  }
}
