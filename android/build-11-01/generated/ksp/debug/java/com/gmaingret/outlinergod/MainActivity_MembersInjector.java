package com.gmaingret.outlinergod;

import com.gmaingret.outlinergod.db.dao.SettingsDao;
import com.gmaingret.outlinergod.repository.AuthRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<SettingsDao> settingsDaoProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  public MainActivity_MembersInjector(Provider<SettingsDao> settingsDaoProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.settingsDaoProvider = settingsDaoProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  public static MembersInjector<MainActivity> create(Provider<SettingsDao> settingsDaoProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new MainActivity_MembersInjector(settingsDaoProvider, authRepositoryProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectSettingsDao(instance, settingsDaoProvider.get());
    injectAuthRepository(instance, authRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.gmaingret.outlinergod.MainActivity.settingsDao")
  public static void injectSettingsDao(MainActivity instance, SettingsDao settingsDao) {
    instance.settingsDao = settingsDao;
  }

  @InjectedFieldSignature("com.gmaingret.outlinergod.MainActivity.authRepository")
  public static void injectAuthRepository(MainActivity instance, AuthRepository authRepository) {
    instance.authRepository = authRepository;
  }
}
