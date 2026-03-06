package com.gmaingret.outlinergod;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.gmaingret.outlinergod.db.AppDatabase;
import com.gmaingret.outlinergod.db.dao.BookmarkDao;
import com.gmaingret.outlinergod.db.dao.DocumentDao;
import com.gmaingret.outlinergod.db.dao.NodeDao;
import com.gmaingret.outlinergod.db.dao.SettingsDao;
import com.gmaingret.outlinergod.di.AuthModule_ProvideDataStoreFactory;
import com.gmaingret.outlinergod.di.ClockModule_ProvideHlcClockFactory;
import com.gmaingret.outlinergod.di.DatabaseModule_ProvideAppDatabaseFactory;
import com.gmaingret.outlinergod.di.DatabaseModule_ProvideBookmarkDaoFactory;
import com.gmaingret.outlinergod.di.DatabaseModule_ProvideDocumentDaoFactory;
import com.gmaingret.outlinergod.di.DatabaseModule_ProvideNodeDaoFactory;
import com.gmaingret.outlinergod.di.DatabaseModule_ProvideSettingsDaoFactory;
import com.gmaingret.outlinergod.di.NetworkModule_ProvideBaseUrlFactory;
import com.gmaingret.outlinergod.di.NetworkModule_ProvideHttpClientFactory;
import com.gmaingret.outlinergod.di.RepositoryModule_ProvideAuthRepositoryFactory;
import com.gmaingret.outlinergod.di.RepositoryModule_ProvideExportRepositoryFactory;
import com.gmaingret.outlinergod.di.RepositoryModule_ProvideSyncRepositoryFactory;
import com.gmaingret.outlinergod.di.WorkManagerModule_ProvideWorkManagerFactory;
import com.gmaingret.outlinergod.repository.AuthRepository;
import com.gmaingret.outlinergod.repository.ExportRepository;
import com.gmaingret.outlinergod.repository.SyncRepository;
import com.gmaingret.outlinergod.repository.impl.AuthRepositoryImpl;
import com.gmaingret.outlinergod.repository.impl.ExportRepositoryImpl;
import com.gmaingret.outlinergod.repository.impl.SyncRepositoryImpl;
import com.gmaingret.outlinergod.sync.HlcClock;
import com.gmaingret.outlinergod.sync.SyncScheduler;
import com.gmaingret.outlinergod.sync.SyncWorker;
import com.gmaingret.outlinergod.sync.SyncWorker_AssistedFactory;
import com.gmaingret.outlinergod.ui.screen.bookmarks.BookmarkListViewModel;
import com.gmaingret.outlinergod.ui.screen.bookmarks.BookmarkListViewModel_HiltModules;
import com.gmaingret.outlinergod.ui.screen.bookmarks.BookmarkListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.bookmarks.BookmarkListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListViewModel;
import com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListViewModel_HiltModules;
import com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.login.LoginViewModel;
import com.gmaingret.outlinergod.ui.screen.login.LoginViewModel_HiltModules;
import com.gmaingret.outlinergod.ui.screen.login.LoginViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.login.LoginViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorViewModel;
import com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorViewModel_HiltModules;
import com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.settings.SettingsViewModel;
import com.gmaingret.outlinergod.ui.screen.settings.SettingsViewModel_HiltModules;
import com.gmaingret.outlinergod.ui.screen.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.gmaingret.outlinergod.ui.screen.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DelegateFactory;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import io.ktor.client.HttpClient;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerOutlinerGodApp_HiltComponents_SingletonC {
  private DaggerOutlinerGodApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public OutlinerGodApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements OutlinerGodApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements OutlinerGodApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements OutlinerGodApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements OutlinerGodApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements OutlinerGodApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements OutlinerGodApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements OutlinerGodApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public OutlinerGodApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends OutlinerGodApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends OutlinerGodApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends OutlinerGodApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends OutlinerGodApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(5).put(BookmarkListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, BookmarkListViewModel_HiltModules.KeyModule.provide()).put(DocumentListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DocumentListViewModel_HiltModules.KeyModule.provide()).put(LoginViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, LoginViewModel_HiltModules.KeyModule.provide()).put(NodeEditorViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, NodeEditorViewModel_HiltModules.KeyModule.provide()).put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectSettingsDao(instance, singletonCImpl.settingsDao());
      MainActivity_MembersInjector.injectAuthRepository(instance, singletonCImpl.provideAuthRepositoryProvider.get());
      return instance;
    }
  }

  private static final class ViewModelCImpl extends OutlinerGodApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<BookmarkListViewModel> bookmarkListViewModelProvider;

    Provider<DocumentListViewModel> documentListViewModelProvider;

    Provider<LoginViewModel> loginViewModelProvider;

    Provider<NodeEditorViewModel> nodeEditorViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.bookmarkListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.documentListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.loginViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.nodeEditorViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(5).put(BookmarkListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (bookmarkListViewModelProvider))).put(DocumentListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (documentListViewModelProvider))).put(LoginViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (loginViewModelProvider))).put(NodeEditorViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (nodeEditorViewModelProvider))).put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider))).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.gmaingret.outlinergod.ui.screen.bookmarks.BookmarkListViewModel
          return (T) new BookmarkListViewModel(singletonCImpl.bookmarkDao(), singletonCImpl.provideAuthRepositoryProvider.get(), singletonCImpl.provideHlcClockProvider.get());

          case 1: // com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListViewModel
          return (T) new DocumentListViewModel(singletonCImpl.documentDao(), singletonCImpl.nodeDao(), singletonCImpl.bookmarkDao(), singletonCImpl.settingsDao(), singletonCImpl.provideAuthRepositoryProvider.get(), singletonCImpl.provideSyncRepositoryProvider.get(), singletonCImpl.provideHlcClockProvider.get(), singletonCImpl.provideDataStoreProvider.get());

          case 2: // com.gmaingret.outlinergod.ui.screen.login.LoginViewModel
          return (T) new LoginViewModel(singletonCImpl.provideAuthRepositoryProvider.get());

          case 3: // com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorViewModel
          return (T) new NodeEditorViewModel(viewModelCImpl.savedStateHandle, singletonCImpl.nodeDao(), singletonCImpl.provideAuthRepositoryProvider.get(), singletonCImpl.provideHlcClockProvider.get(), singletonCImpl.provideSyncRepositoryProvider.get(), singletonCImpl.documentDao(), singletonCImpl.bookmarkDao(), singletonCImpl.settingsDao(), singletonCImpl.provideDataStoreProvider.get());

          case 4: // com.gmaingret.outlinergod.ui.screen.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.settingsDao(), singletonCImpl.provideAuthRepositoryProvider.get(), singletonCImpl.provideHlcClockProvider.get(), singletonCImpl.provideExportRepositoryProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends OutlinerGodApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends OutlinerGodApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends OutlinerGodApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<HttpClient> provideHttpClientProvider;

    Provider<DataStore<Preferences>> provideDataStoreProvider;

    Provider<AuthRepository> provideAuthRepositoryProvider;

    Provider<SyncRepository> provideSyncRepositoryProvider;

    Provider<AppDatabase> provideAppDatabaseProvider;

    Provider<SyncWorker_AssistedFactory> syncWorker_AssistedFactoryProvider;

    Provider<WorkManager> provideWorkManagerProvider;

    Provider<SyncScheduler> syncSchedulerProvider;

    Provider<HlcClock> provideHlcClockProvider;

    Provider<ExportRepository> provideExportRepositoryProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    AuthRepositoryImpl authRepositoryImpl() {
      return new AuthRepositoryImpl(provideHttpClientProvider.get(), provideDataStoreProvider.get(), NetworkModule_ProvideBaseUrlFactory.provideBaseUrl());
    }

    SyncRepositoryImpl syncRepositoryImpl() {
      return new SyncRepositoryImpl(provideHttpClientProvider.get(), NetworkModule_ProvideBaseUrlFactory.provideBaseUrl());
    }

    NodeDao nodeDao() {
      return DatabaseModule_ProvideNodeDaoFactory.provideNodeDao(provideAppDatabaseProvider.get());
    }

    DocumentDao documentDao() {
      return DatabaseModule_ProvideDocumentDaoFactory.provideDocumentDao(provideAppDatabaseProvider.get());
    }

    BookmarkDao bookmarkDao() {
      return DatabaseModule_ProvideBookmarkDaoFactory.provideBookmarkDao(provideAppDatabaseProvider.get());
    }

    SettingsDao settingsDao() {
      return DatabaseModule_ProvideSettingsDaoFactory.provideSettingsDao(provideAppDatabaseProvider.get());
    }

    Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return Collections.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>singletonMap("com.gmaingret.outlinergod.sync.SyncWorker", ((Provider) (syncWorker_AssistedFactoryProvider)));
    }

    HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    ExportRepositoryImpl exportRepositoryImpl() {
      return new ExportRepositoryImpl(provideHttpClientProvider.get(), NetworkModule_ProvideBaseUrlFactory.provideBaseUrl(), ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideHttpClientProvider = new DelegateFactory<>();
      this.provideDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 4));
      this.provideAuthRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<AuthRepository>(singletonCImpl, 3));
      DelegateFactory.setDelegate(provideHttpClientProvider, DoubleCheck.provider(new SwitchingProvider<HttpClient>(singletonCImpl, 2)));
      this.provideSyncRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SyncRepository>(singletonCImpl, 1));
      this.provideAppDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 5));
      this.syncWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<SyncWorker_AssistedFactory>(singletonCImpl, 0));
      this.provideWorkManagerProvider = DoubleCheck.provider(new SwitchingProvider<WorkManager>(singletonCImpl, 7));
      this.syncSchedulerProvider = DoubleCheck.provider(new SwitchingProvider<SyncScheduler>(singletonCImpl, 6));
      this.provideHlcClockProvider = DoubleCheck.provider(new SwitchingProvider<HlcClock>(singletonCImpl, 8));
      this.provideExportRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ExportRepository>(singletonCImpl, 9));
    }

    @Override
    public void injectOutlinerGodApp(OutlinerGodApp outlinerGodApp) {
      injectOutlinerGodApp2(outlinerGodApp);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private OutlinerGodApp injectOutlinerGodApp2(OutlinerGodApp instance) {
      OutlinerGodApp_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      OutlinerGodApp_MembersInjector.injectSyncScheduler(instance, syncSchedulerProvider.get());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.gmaingret.outlinergod.sync.SyncWorker_AssistedFactory
          return (T) new SyncWorker_AssistedFactory() {
            @Override
            public SyncWorker create(Context appContext, WorkerParameters workerParams) {
              return new SyncWorker(appContext, workerParams, singletonCImpl.provideSyncRepositoryProvider.get(), singletonCImpl.provideAuthRepositoryProvider.get(), singletonCImpl.nodeDao(), singletonCImpl.documentDao(), singletonCImpl.bookmarkDao(), singletonCImpl.settingsDao(), singletonCImpl.provideDataStoreProvider.get());
            }
          };

          case 1: // com.gmaingret.outlinergod.repository.SyncRepository
          return (T) RepositoryModule_ProvideSyncRepositoryFactory.provideSyncRepository(singletonCImpl.syncRepositoryImpl());

          case 2: // io.ktor.client.HttpClient
          return (T) NetworkModule_ProvideHttpClientFactory.provideHttpClient(DoubleCheck.lazy(singletonCImpl.provideAuthRepositoryProvider));

          case 3: // com.gmaingret.outlinergod.repository.AuthRepository
          return (T) RepositoryModule_ProvideAuthRepositoryFactory.provideAuthRepository(singletonCImpl.authRepositoryImpl());

          case 4: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
          return (T) AuthModule_ProvideDataStoreFactory.provideDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.gmaingret.outlinergod.db.AppDatabase
          return (T) DatabaseModule_ProvideAppDatabaseFactory.provideAppDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 6: // com.gmaingret.outlinergod.sync.SyncScheduler
          return (T) new SyncScheduler(singletonCImpl.provideWorkManagerProvider.get());

          case 7: // androidx.work.WorkManager
          return (T) WorkManagerModule_ProvideWorkManagerFactory.provideWorkManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 8: // com.gmaingret.outlinergod.sync.HlcClock
          return (T) ClockModule_ProvideHlcClockFactory.provideHlcClock();

          case 9: // com.gmaingret.outlinergod.repository.ExportRepository
          return (T) RepositoryModule_ProvideExportRepositoryFactory.provideExportRepository(singletonCImpl.exportRepositoryImpl());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
