package com.gmaingret.outlinergod.sync;

import android.content.Context;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.work.WorkerParameters;
import com.gmaingret.outlinergod.db.dao.BookmarkDao;
import com.gmaingret.outlinergod.db.dao.DocumentDao;
import com.gmaingret.outlinergod.db.dao.NodeDao;
import com.gmaingret.outlinergod.db.dao.SettingsDao;
import com.gmaingret.outlinergod.repository.AuthRepository;
import com.gmaingret.outlinergod.repository.SyncRepository;
import dagger.internal.DaggerGenerated;
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
public final class SyncWorker_Factory {
  private final Provider<SyncRepository> syncRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<NodeDao> nodeDaoProvider;

  private final Provider<DocumentDao> documentDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<SettingsDao> settingsDaoProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public SyncWorker_Factory(Provider<SyncRepository> syncRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<NodeDao> nodeDaoProvider,
      Provider<DocumentDao> documentDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<SettingsDao> settingsDaoProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.syncRepositoryProvider = syncRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.nodeDaoProvider = nodeDaoProvider;
    this.documentDaoProvider = documentDaoProvider;
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.settingsDaoProvider = settingsDaoProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  public SyncWorker get(Context appContext, WorkerParameters workerParams) {
    return newInstance(appContext, workerParams, syncRepositoryProvider.get(), authRepositoryProvider.get(), nodeDaoProvider.get(), documentDaoProvider.get(), bookmarkDaoProvider.get(), settingsDaoProvider.get(), dataStoreProvider.get());
  }

  public static SyncWorker_Factory create(Provider<SyncRepository> syncRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<NodeDao> nodeDaoProvider,
      Provider<DocumentDao> documentDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<SettingsDao> settingsDaoProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new SyncWorker_Factory(syncRepositoryProvider, authRepositoryProvider, nodeDaoProvider, documentDaoProvider, bookmarkDaoProvider, settingsDaoProvider, dataStoreProvider);
  }

  public static SyncWorker newInstance(Context appContext, WorkerParameters workerParams,
      SyncRepository syncRepository, AuthRepository authRepository, NodeDao nodeDao,
      DocumentDao documentDao, BookmarkDao bookmarkDao, SettingsDao settingsDao,
      DataStore<Preferences> dataStore) {
    return new SyncWorker(appContext, workerParams, syncRepository, authRepository, nodeDao, documentDao, bookmarkDao, settingsDao, dataStore);
  }
}
