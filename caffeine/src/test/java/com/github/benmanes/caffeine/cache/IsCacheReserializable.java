/*
 * Copyright 2015 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.github.benmanes.caffeine.cache.Caffeine.AsyncWeigher;
import com.github.benmanes.caffeine.cache.Caffeine.BoundedWeigher;
import com.github.benmanes.caffeine.matchers.DescriptionBuilder;
import com.google.common.testing.SerializableTester;

/**
 * A matcher that evaluates a cache by creating a serialized copy and checking its equality.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsCacheReserializable<T> extends TypeSafeDiagnosingMatcher<T> {
  DescriptionBuilder desc;

  private IsCacheReserializable() {}

  @Override
  public void describeTo(Description description) {
    description.appendValue("serialized copy");
    if (desc.getDescription() != description) {
      description.appendText(desc.getDescription().toString());
    }
  }

  @Override
  public boolean matchesSafely(T original, Description description) {
    desc = new DescriptionBuilder(description);

    T copy = SerializableTester.reserialize(original);

    if (original instanceof AsyncLoadingCache<?, ?>) {
      @SuppressWarnings("unchecked")
      AsyncLoadingCache<Object, Object> asyncCache = (AsyncLoadingCache<Object, Object>) original;
      @SuppressWarnings("unchecked")
      AsyncLoadingCache<Object, Object> asyncCopy = (AsyncLoadingCache<Object, Object>) copy;
      checkAsynchronousCache(asyncCache, asyncCopy, desc);
    } else if (original instanceof Cache<?, ?>) {
      @SuppressWarnings("unchecked")
      Cache<Object, Object> syncCache = (Cache<Object, Object>) original;
      @SuppressWarnings("unchecked")
      Cache<Object, Object> syncCopy = (Cache<Object, Object>) copy;
      checkSyncronousCache(syncCache, syncCopy, desc);
    } else {
      throw new UnsupportedOperationException();
    }

    return desc.matches();
  }

  private static <K, V> void checkAsynchronousCache(AsyncLoadingCache<K, V> original,
      AsyncLoadingCache<K, V> copy, DescriptionBuilder desc) {
    if (!IsValidAsyncCache.<K, V>validAsyncCache().matchesSafely(copy, desc.getDescription())) {
      desc.expected("valid async cache");
    } else if (original instanceof UnboundedLocalCache.LocalAsyncLoadingCache<?, ?>) {
      checkUnboundedLocalAsyncLoadingCache(
          (UnboundedLocalCache.LocalAsyncLoadingCache<K, V>) original,
          (UnboundedLocalCache.LocalAsyncLoadingCache<K, V>) copy, desc);
    } else if (original instanceof BoundedLocalCache.LocalAsyncLoadingCache<?, ?>) {
      checkBoundedLocalAsyncLoadingCache(
          (BoundedLocalCache.LocalAsyncLoadingCache<K, V>) original,
          (BoundedLocalCache.LocalAsyncLoadingCache<K, V>) copy, desc);
    }
  }

  private static <K, V> void checkSyncronousCache(Cache<K, V> original, Cache<K, V> copy,
      DescriptionBuilder desc) {
    if (!IsValidCache.<K, V>validCache().matchesSafely(copy, desc.getDescription())) {
      desc.expected("valid cache");
      return;
    }

    checkIfUnbounded(original, copy, desc);
    checkIfBounded(original, copy, desc);
  }

  /* ---------------- Unbounded -------------- */

  private static <K, V> void checkIfUnbounded(
      Cache<K, V> original, Cache<K, V> copy, DescriptionBuilder desc) {
    if (original instanceof UnboundedLocalCache.LocalManualCache<?, ?>) {
      checkUnoundedLocalManualCache((UnboundedLocalCache.LocalManualCache<K, V>) original,
          (UnboundedLocalCache.LocalManualCache<K, V>) copy, desc);
    }
    if (original instanceof UnboundedLocalCache.LocalLoadingCache<?, ?>) {
      checkUnboundedLocalLoadingCache((UnboundedLocalCache.LocalLoadingCache<K, V>) original,
          (UnboundedLocalCache.LocalLoadingCache<K, V>) copy, desc);
    }
    if (original instanceof UnboundedLocalCache.LocalAsyncLoadingCache<?, ?>.LoadingCacheView) {
      checkUnboundedLocalAsyncLoadingCache(
          ((UnboundedLocalCache.LocalAsyncLoadingCache<K, V>.LoadingCacheView) original).getOuter(),
          ((UnboundedLocalCache.LocalAsyncLoadingCache<K, V>.LoadingCacheView) copy).getOuter(),
          desc);
    }
  }

  private static <K, V> void checkUnoundedLocalManualCache(
      UnboundedLocalCache.LocalManualCache<K, V> original,
      UnboundedLocalCache.LocalManualCache<K, V> copy, DescriptionBuilder desc) {
    checkUnboundedLocalCache(original.cache, copy.cache, desc);
  }

  private static <K, V> void checkUnboundedLocalLoadingCache(
      UnboundedLocalCache.LocalLoadingCache<K, V> original,
      UnboundedLocalCache.LocalLoadingCache<K, V> copy, DescriptionBuilder desc) {
    desc.expectThat("same cacheLoader", copy.loader, is(original.loader));
  }

  private static <K, V> void checkUnboundedLocalAsyncLoadingCache(
      UnboundedLocalCache.LocalAsyncLoadingCache<K, V> original,
      UnboundedLocalCache.LocalAsyncLoadingCache<K, V> copy, DescriptionBuilder desc) {
    checkUnboundedLocalCache(original.cache, copy.cache, desc);
    desc.expectThat("same cacheLoader", copy.loader, is(original.loader));
  }

  private static <K, V> void checkUnboundedLocalCache(UnboundedLocalCache<K, V> original,
      UnboundedLocalCache<K, V> copy, DescriptionBuilder desc) {
    desc.expectThat("estimated empty", copy.mappingCount(), is(0L));
    desc.expectThat("same ticker", copy.ticker, is(original.ticker));
    desc.expectThat("same isRecordingStats",
        copy.isRecordingStats, is(original.isRecordingStats));
    if (original.removalListener == null) {
      desc.expectThat("same removalListener", copy.removalListener, is(nullValue()));
    } else if (copy.removalListener == null) {
      desc.expected("non-null removalListener");
    } else if (copy.removalListener.getClass() != original.removalListener.getClass()) {
      desc.expected("same removalListener but was " + copy.removalListener.getClass());
    }
  }

  /* ---------------- Bounded -------------- */

  private static <K, V> void checkIfBounded(
      Cache<K, V> original, Cache<K, V> copy, DescriptionBuilder desc) {
    if (original instanceof BoundedLocalCache.LocalManualCache<?, ?>) {
      checkBoundedLocalManualCache((BoundedLocalCache.LocalManualCache<K, V>) original,
          (BoundedLocalCache.LocalManualCache<K, V>) copy, desc);
    }
    if (original instanceof BoundedLocalCache.LocalLoadingCache<?, ?>) {
      checkBoundedLocalLoadingCache((BoundedLocalCache.LocalLoadingCache<K, V>) original,
          (BoundedLocalCache.LocalLoadingCache<K, V>) copy, desc);
    }
    if (original instanceof BoundedLocalCache.LocalAsyncLoadingCache<?, ?>.LoadingCacheView) {
      checkBoundedLocalAsyncLoadingCache(
          ((BoundedLocalCache.LocalAsyncLoadingCache<K, V>.LoadingCacheView) original).getOuter(),
          ((BoundedLocalCache.LocalAsyncLoadingCache<K, V>.LoadingCacheView) copy).getOuter(),
          desc);
    }
  }

  private static <K, V> void checkBoundedLocalManualCache(
      BoundedLocalCache.LocalManualCache<K, V> original,
      BoundedLocalCache.LocalManualCache<K, V> copy, DescriptionBuilder desc) {
    checkBoundedLocalCache(original.cache, copy.cache, desc);
  }

  private static <K, V> void checkBoundedLocalLoadingCache(
      BoundedLocalCache.LocalLoadingCache<K, V> original,
      BoundedLocalCache.LocalLoadingCache<K, V> copy, DescriptionBuilder desc) {
    desc.expectThat("same cacheLoader", copy.cache.loader, is(original.cache.loader));
  }

  private static <K, V> void checkBoundedLocalAsyncLoadingCache(
      BoundedLocalCache.LocalAsyncLoadingCache<K, V> original,
      BoundedLocalCache.LocalAsyncLoadingCache<K, V> copy, DescriptionBuilder desc) {
    checkBoundedLocalCache(original.cache, copy.cache, desc);
    desc.expectThat("same cacheLoader", copy.loader, is(original.loader));
  }

  private static <K, V> void checkBoundedLocalCache(BoundedLocalCache<K, V> original,
      BoundedLocalCache<K, V> copy, DescriptionBuilder desc) {
    desc.expectThat("empty", copy.mappingCount(), is(0L));
    desc.expectThat("same weigher",
        unwrapWeigher(copy.weigher).getClass(), is(equalTo(
        unwrapWeigher(original.weigher).getClass())));
    desc.expectThat("same keyStrategy", copy.keyStrategy, is(original.keyStrategy));
    desc.expectThat("same valueStrategy",
        copy.valueStrategy, is(original.valueStrategy));
    if (copy.maximumWeightedSize == null) {
      desc.expectThat("null maximumWeight", copy.maximumWeightedSize, is(nullValue()));
    } else {
      desc.expectThat("same maximumWeight",
          copy.maximumWeightedSize.get(), is(original.maximumWeightedSize.get()));
    }

    desc.expectThat("same expireAfterWriteNanos",
        copy.expireAfterWriteNanos, is(original.expireAfterWriteNanos));
    desc.expectThat("same expireAfterWriteNanos",
        copy.expireAfterWriteNanos, is(original.expireAfterWriteNanos));
    desc.expectThat("same expireAfterWriteNanos",
        copy.expireAfterWriteNanos, is(original.expireAfterWriteNanos));

    if (original.removalListener == null) {
      desc.expectThat("same removalListener", copy.removalListener, is(nullValue()));
    } else if (copy.removalListener == null) {
      desc.expected("non-null removalListener");
    } else if (copy.removalListener.getClass() != original.removalListener.getClass()) {
      desc.expected("same removalListener but was " + copy.removalListener.getClass());
    }
  }

  /* ---------------- Shared -------------- */

  private static <K, V> Weigher<K, V> unwrapWeigher(Weigher<?, ?> weigher) {
    for (;;) {
      if (weigher instanceof BoundedWeigher<?, ?>) {
        weigher = ((BoundedWeigher<?, ?>) weigher).delegate;
      } else if (weigher instanceof AsyncWeigher<?, ?>) {
        weigher = ((AsyncWeigher<?, ?>) weigher).delegate;
      } else {
        @SuppressWarnings("unchecked")
        Weigher<K, V> castedWeigher = (Weigher<K, V>) weigher;
        return castedWeigher;
      }
    }
  }

  @Factory
  public static <T> Matcher<T> reserializable() {
    return new IsCacheReserializable<T>();
  }
}