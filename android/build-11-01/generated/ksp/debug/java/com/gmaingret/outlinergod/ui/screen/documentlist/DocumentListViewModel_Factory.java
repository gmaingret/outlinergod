package com.gmaingret.outlinergod.ui.screen.documentlist;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class DocumentListViewModel_Factory implements Factory<DocumentListViewModel> {
  private final Provider<DocumentDao> documentDaoProvider;

  private final Provider<NodeDao> nodeDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<SettingsDao> settingsDaoProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<SyncRepository> syncRepositoryProvider;

  private final Provider<HlcClock> hlcClockProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public DocumentListViewModel_Factory(Provider<DocumentDao> documentDaoProvider,
      Provider<NodeDao> nodeDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<SettingsDao> settingsDaoProvider, Provider<AuthRepository> authRepositoryProvider,
      Provider<SyncRepository> syncRepositoryProvider, Provider<HlcClock> hlcClockProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.documentDaoProvider = documentDaoProvider;
    this.nodeDaoProvider = nodeDaoProvider;
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.settingsDaoProvider = settingsDaoProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.syncRepositoryProvider = syncRepositoryProvider;
    this.hlcClockProvider = hlcClockProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public DocumentListViewModel get() {
    return newInstance(documentDaoProvider.get(), nodeDaoProvider.get(), bookmarkDaoProvider.get(), settingsDaoProvider.get(), authRepositoryProvider.get(), syncRepositoryProvider.get(), hlcClockProvider.get(), dataStoreProvider.get());
  }

  public static DocumentListViewModel_Factory create(Provider<DocumentDao> documentDaoProvider,
      Provider<NodeDao> nodeDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<SettingsDao> settingsDaoProvider, Provider<AuthRepository> authRepositoryProvider,
      Provider<SyncRepository> syncRepositoryProvider, Provider<HlcClock> hlcClockProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new DocumentListViewModel_Factory(documentDaoProvider, nodeDaoProvider, bookmarkDaoProvider, settingsDaoProvider, authRepositoryProvider, syncRepositoryProvider, hlcClockProvider, dataStoreProvider);
  }

  public static DocumentListViewModel newInstance(DocumentDao documentDao, NodeDao nodeDao,
      BookmarkDao bookmarkDao, SettingsDao settingsDao, AuthRepository authRepository,
      SyncRepository syncRepository, HlcClock hlcClock, DataStore<Preferences> dataStore) {
    return new DocumentListViewModel(documentDao, nodeDao, bookmarkDao, settingsDao, authRepository, syncRepository, hlcClock, dataStore);
  }
}
