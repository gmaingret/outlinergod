package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.db.AppDatabase;
import com.gmaingret.outlinergod.db.dao.SettingsDao;
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
public final class DatabaseModule_ProvideSettingsDaoFactory implements Factory<SettingsDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideSettingsDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public SettingsDao get() {
    return provideSettingsDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideSettingsDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideSettingsDaoFactory(dbProvider);
  }

  public static SettingsDao provideSettingsDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSettingsDao(db));
  }
}
