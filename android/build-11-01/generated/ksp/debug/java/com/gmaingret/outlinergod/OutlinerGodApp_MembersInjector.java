package com.gmaingret.outlinergod;

import androidx.hilt.work.HiltWorkerFactory;
import com.gmaingret.outlinergod.sync.SyncScheduler;
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
public final class OutlinerGodApp_MembersInjector implements MembersInjector<OutlinerGodApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private final Provider<SyncScheduler> syncSchedulerProvider;

  public OutlinerGodApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<SyncScheduler> syncSchedulerProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
    this.syncSchedulerProvider = syncSchedulerProvider;
  }

  public static MembersInjector<OutlinerGodApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<SyncScheduler> syncSchedulerProvider) {
    return new OutlinerGodApp_MembersInjector(workerFactoryProvider, syncSchedulerProvider);
  }

  @Override
  public void injectMembers(OutlinerGodApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
    injectSyncScheduler(instance, syncSchedulerProvider.get());
  }

  @InjectedFieldSignature("com.gmaingret.outlinergod.OutlinerGodApp.workerFactory")
  public static void injectWorkerFactory(OutlinerGodApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }

  @InjectedFieldSignature("com.gmaingret.outlinergod.OutlinerGodApp.syncScheduler")
  public static void injectSyncScheduler(OutlinerGodApp instance, SyncScheduler syncScheduler) {
    instance.syncScheduler = syncScheduler;
  }
}
