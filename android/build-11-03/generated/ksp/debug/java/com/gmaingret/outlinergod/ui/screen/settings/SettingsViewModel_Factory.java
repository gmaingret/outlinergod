package com.gmaingret.outlinergod.ui.screen.settings;

import com.gmaingret.outlinergod.db.dao.SettingsDao;
import com.gmaingret.outlinergod.repository.AuthRepository;
import com.gmaingret.outlinergod.repository.ExportRepository;
import com.gmaingret.outlinergod.sync.HlcClock;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsDao> settingsDaoProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<HlcClock> hlcClockProvider;

  private final Provider<ExportRepository> exportRepositoryProvider;

  public SettingsViewModel_Factory(Provider<SettingsDao> settingsDaoProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<HlcClock> hlcClockProvider,
      Provider<ExportRepository> exportRepositoryProvider) {
    this.settingsDaoProvider = settingsDaoProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.hlcClockProvider = hlcClockProvider;
    this.exportRepositoryProvider = exportRepositoryProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(settingsDaoProvider.get(), authRepositoryProvider.get(), hlcClockProvider.get(), exportRepositoryProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<SettingsDao> settingsDaoProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<HlcClock> hlcClockProvider,
      Provider<ExportRepository> exportRepositoryProvider) {
    return new SettingsViewModel_Factory(settingsDaoProvider, authRepositoryProvider, hlcClockProvider, exportRepositoryProvider);
  }

  public static SettingsViewModel newInstance(SettingsDao settingsDao,
      AuthRepository authRepository, HlcClock hlcClock, ExportRepository exportRepository) {
    return new SettingsViewModel(settingsDao, authRepository, hlcClock, exportRepository);
  }
}
