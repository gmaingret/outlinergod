package com.gmaingret.outlinergod.ui.screen.bookmarks;

import com.gmaingret.outlinergod.db.dao.BookmarkDao;
import com.gmaingret.outlinergod.repository.AuthRepository;
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
public final class BookmarkListViewModel_Factory implements Factory<BookmarkListViewModel> {
  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<HlcClock> hlcClockProvider;

  public BookmarkListViewModel_Factory(Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<HlcClock> hlcClockProvider) {
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.hlcClockProvider = hlcClockProvider;
  }

  @Override
  public BookmarkListViewModel get() {
    return newInstance(bookmarkDaoProvider.get(), authRepositoryProvider.get(), hlcClockProvider.get());
  }

  public static BookmarkListViewModel_Factory create(Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<AuthRepository> authRepositoryProvider, Provider<HlcClock> hlcClockProvider) {
    return new BookmarkListViewModel_Factory(bookmarkDaoProvider, authRepositoryProvider, hlcClockProvider);
  }

  public static BookmarkListViewModel newInstance(BookmarkDao bookmarkDao,
      AuthRepository authRepository, HlcClock hlcClock) {
    return new BookmarkListViewModel(bookmarkDao, authRepository, hlcClock);
  }
}
