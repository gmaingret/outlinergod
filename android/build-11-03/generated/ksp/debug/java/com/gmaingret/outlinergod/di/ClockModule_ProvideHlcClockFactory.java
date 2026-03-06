package com.gmaingret.outlinergod.di;

import com.gmaingret.outlinergod.sync.HlcClock;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class ClockModule_ProvideHlcClockFactory implements Factory<HlcClock> {
  @Override
  public HlcClock get() {
    return provideHlcClock();
  }

  public static ClockModule_ProvideHlcClockFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static HlcClock provideHlcClock() {
    return Preconditions.checkNotNullFromProvides(ClockModule.INSTANCE.provideHlcClock());
  }

  private static final class InstanceHolder {
    static final ClockModule_ProvideHlcClockFactory INSTANCE = new ClockModule_ProvideHlcClockFactory();
  }
}
