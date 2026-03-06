package com.gmaingret.outlinergod.repository.impl;

import com.gmaingret.outlinergod.db.dao.NodeDao;
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
public final class SearchRepositoryImpl_Factory implements Factory<SearchRepositoryImpl> {
  private final Provider<NodeDao> nodeDaoProvider;

  public SearchRepositoryImpl_Factory(Provider<NodeDao> nodeDaoProvider) {
    this.nodeDaoProvider = nodeDaoProvider;
  }

  @Override
  public SearchRepositoryImpl get() {
    return newInstance(nodeDaoProvider.get());
  }

  public static SearchRepositoryImpl_Factory create(Provider<NodeDao> nodeDaoProvider) {
    return new SearchRepositoryImpl_Factory(nodeDaoProvider);
  }

  public static SearchRepositoryImpl newInstance(NodeDao nodeDao) {
    return new SearchRepositoryImpl(nodeDao);
  }
}
