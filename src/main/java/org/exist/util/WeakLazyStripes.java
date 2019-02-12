/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util;

import net.jcip.annotations.ThreadSafe;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Inspired by Guava's com.google.common.util.concurrent.Striped#lazyWeakReadWriteLock(int)
 * implementation.
 * {@see https://google.github.io/guava/releases/21.0/api/docs/com/google/common/util/concurrent/Striped.html#lazyWeakReadWriteLock-int-}
 *
 * However this is much simpler, and there is no hashing; we
 * will always return the same object (stripe) for the same key.
 *
 * This class basically couples Weak References with a
 * ConcurrentHashMap and manages draining expired Weak
 * References from the HashMap.
 *
 * @param <K> The type of the key for the stripe.
 * @param <S> The type of the stripe.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class WeakLazyStripes<K, S> {
    private static final int INITIAL_CAPACITY = 1000;
    private static final float LOAD_FACTOR = 0.75f;
    private static final int MAX_EXPIRED_REFERENCE_READ_COUNT = 1000;

    private final ReferenceQueue<S> referenceQueue;
    private final ConcurrentMap<K, WeakReference<S>> stripes;
    private final AtomicInteger expiredReferenceReadCount = new AtomicInteger();

    private final Function<K, S> creator;

    /**
     * Constructs a WeakLazyStripes where the concurrencyLevel
     * is the lower of either {@link ConcurrentHashMap#DEFAULT_CONCURRENCY_LEVEL}
     * or {@code Runtime.getRuntime().availableProcessors() * 2}.
     *
     * @param creator A factory for creating new Stripes when needed
     */
    public WeakLazyStripes(final Function<K, S> creator) {
       this(Math.min(16, Runtime.getRuntime().availableProcessors() * 2), creator);  // 16 == ConcurrentHashMap#DEFAULT_CONCURRENCY_LEVEL
    }

    /**
     * Constructs a WeakLazyStripes.
     *
     * @param concurrencyLevel The concurrency level for the underlying
     *     {@link ConcurrentHashMap#ConcurrentHashMap(int, float, int)}
     * @param creator A factory for creating new Stripes when needed
     */
    public WeakLazyStripes(final int concurrencyLevel, final Function<K, S> creator) {
        this.stripes = new ConcurrentHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, concurrencyLevel);
        this.referenceQueue = new ReferenceQueue<>();
        this.creator = creator;
    }

    /**
     * Get the stripe for the given key
     *
     * If the stripe does not exist, it will be created by
     * calling {@link Function#apply(Object)} on {@link this#creator}
     *
     * @param key the key for the stripe
     * @return the stripe
     */
    public S get(final K key) {
        final WeakReference<S> stripeRef = stripes.compute(key, (k, valueRef) -> {
            if(valueRef == null) {
                return new WeakReference<>(creator.apply(k), referenceQueue);
            } else if(valueRef.get() == null) {
                expiredReferenceReadCount.incrementAndGet();
                return new WeakReference<>(creator.apply(k), referenceQueue);
            } else {
                return valueRef;
            }
        });

        // have we reached the threshold where we should clear
        // out any cleared WeakReferences from the stripes map
        final int count = expiredReferenceReadCount.get();
        if(count > MAX_EXPIRED_REFERENCE_READ_COUNT
                && expiredReferenceReadCount.compareAndSet(count, 0)) {
            drainClearedReferences();
        }

        // check the weak reference before returning!
        final S stripe = stripeRef.get();
        if(stripe != null) {
            return stripe;
        }

        // weak reference has expired in the mean time, regenerate
        return get(key);
    }

    /**
     * Removes any cleared WeakReferences
     * from the stripes map
     */
    private void drainClearedReferences() {
        Reference<? extends S> ref;
        while ((ref = referenceQueue.poll()) != null) {
            @SuppressWarnings("unchecked")
            final WeakReference<? extends S> stripeRef = (WeakReference<? extends S>)ref;

            /*
                If this is too slow, we could store Map<Key, WeakReference<Tuple2<Key, Value>>>
                to sacrifice a small amount of memory for remove performance by then calling Map#remove(key)
                instead of calling the iterative function Map#values()#remove(value)
             */
            stripes.values().remove(stripeRef);
        }
    }
}
