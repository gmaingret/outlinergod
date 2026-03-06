package com.gmaingret.outlinergod.ui.screen.nodeeditor;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.lifecycle.SavedStateHandle;
import com.gmaingret.outlinergod.db.dao.BookmarkDao;
import com.gmaingret.outlinergod.db.dao.DocumentDao;
import com.gmaingret.outlinergod.db.dao.NodeDao;
import com.gmaingret.outlinergod.db.dao.SettingsDao;
import com.gmaingret.outlinergod.repository.AuthRepository;
import com.gmaingret.outlinergod.repository.SyncRepository;
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
public final class NodeEditorViewModel_Factory implements Factory<NodeEditorViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<NodeDao> nodeDaoProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<HlcClock> hlcClockProvider;

  private final Provider<SyncRepository> syncRepositoryProvider;

  private final Provider<DocumentDao> documentDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<SettingsDao> settingsDaoProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public NodeEditorViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<NodeDao> nodeDaoProvider, Provider<AuthRepository> authRepositoryProvider,
      Provider<HlcClock> hlcClockProvider, Provider<SyncRepository> syncRepositoryProvider,
      Provider<DocumentDao> documentDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<SettingsDao> settingsDaoProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.nodeDaoProvider = nodeDaoProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.hlcClockProvider = hlcClockProvider;
    this.syncRepositoryProvider = syncRepositoryProvider;
    this.documentDaoProvider = documentDaoProvider;
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.settingsDaoProvider = settingsDaoProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public NodeEditorViewModel get() {
    return newInstance(savedStateHandleProvider.get(), nodeDaoProvider.get(), authRepositoryProvider.get(), hlcClockProvider.get(), syncRepositoryProvider.get(), documentDaoProvider.get(), bookmarkDaoProvider.get(), settingsDaoProvider.get(), dataStoreProvider.get());
  }

  public static NodeEditorViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider, Provider<NodeDao> nodeDaoProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<HlcClock> hlcClockProvider,
      Provider<SyncRepository> syncRepositoryProvider, Provider<DocumentDao> documentDaoProvider,
      Provider<BookmarkDao> bookmarkDaoProvider, Provider<SettingsDao> settingsDaoProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new NodeEditorViewModel_Factory(savedStateHandleProvider, nodeDaoProvider, authRepositoryProvider, hlcClockProvider, syncRepositoryProvider, documentDaoProvider, bookmarkDaoProvider, settingsDaoProvider, dataStoreProvider);
  }

  public static NodeEditorViewModel newInstance(SavedStateHandle savedStateHandle, NodeDao nodeDao,
      AuthRepository authRepository, HlcClock hlcClock, SyncRepository syncRepository,
      DocumentDao documentDao, BookmarkDao bookmarkDao, SettingsDao settingsDao,
      DataStore<Preferences> dataStore) {
    return new NodeEditorViewModel(savedStateHandle, nodeDao, authRepository, hlcClock, syncRepository, documentDao, bookmarkDao, settingsDao, dataStore);
  }
}
