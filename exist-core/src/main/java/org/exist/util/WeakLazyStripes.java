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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import javax.annotation.Nullable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

/**
 * Inspired by Guava's com.google.common.util.concurrent.Striped#lazyWeakReadWriteLock(int)
 * implementation.
 * See <a href="https://google.github.io/guava/releases/21.0/api/docs/com/google/common/util/concurrent/Striped.html#lazyWeakReadWriteLock-int-">https://google.github.io/guava/releases/21.0/api/docs/com/google/common/util/concurrent/Striped.html#lazyWeakReadWriteLock-int-</a>.
 *
 * However this is much simpler, and there is no hashing; we
 * will always return the same object (stripe) for the same key.
 *
 * This class basically couples Weak References with a
 * thread safe HashMap and manages draining expired Weak
 * References from the HashMap.
 *
 * Weak References will be cleaned up from the internal map
 * after they have been cleared by the GC. Two cleanup policies
 * are provided: "Batch" and "Amortize". The policy is chosen
 * by the constructor parameter {@code amortizeCleanup}.
 *
 * Batch Cleanup
 *     With Batch Cleanup, expired Weak References will
 *     be collected up to the {@link #MAX_EXPIRED_REFERENCE_READ_COUNT}
 *     limit, at which point the calling thread which causes
 *     that ceiling to be detected will cleanup all expired references.
 *
 * Amortize Cleanup
 *     With Amortize Cleanup, each calling thread will attempt
 *     to cleanup up to {@link #DRAIN_MAX} expired weak
 *     references on each write operation, or after
 *     {@link #READ_DRAIN_THRESHOLD} since the last cleanup.
 *
 * With either cleanup policy, only a single calling thread
 * performs the cleanup at any time.
 *
 * @param <K> The type of the key for the stripe.
 * @param <S> The type of the stripe.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class WeakLazyStripes<K, S> {
    private static final int INITIAL_CAPACITY = 1000;
    private static final float LOAD_FACTOR = 0.75f;

    /**
     * When {@link #amortizeCleanup} is false, this is the
     * number of reads allowed which return expired references
     * before calling {@link #drainClearedReferences()}.
     */
    private static final int MAX_EXPIRED_REFERENCE_READ_COUNT = 1000;

    /**
     * When {@link #amortizeCleanup} is true, this is the
     * number of reads which are performed between calls
     * to {@link #drainClearedReferences()}.
     */
    private static final int READ_DRAIN_THRESHOLD = 64;

    /**
     * When {@link #amortizeCleanup} is true, this is the
     * maximum number of entries to be drained
     * by {@link #drainClearedReferences()}.
     */
    private static final int DRAIN_MAX = 16;

    private final ReferenceQueue<S> referenceQueue;

    private final StampedLock stripesLock = new StampedLock();
    @GuardedBy("stripesLock") private final Object2ObjectOpenHashMap<K, WeakValueReference<K, S>> stripes;

    /**
     * The number of reads on {@link #stripes} which have returned
     * expired weak references.
     */
    private final AtomicInteger expiredReferenceReadCount = new AtomicInteger();

    /**
     * The number of reads on {@link #stripes} since
     * {@link #drainClearedReferences()} was last
     * completed.
     */
    private final AtomicInteger readCount = new AtomicInteger();

    private final Function<K, S> creator;
    private final boolean amortizeCleanup;

    /**
     * Guard so that only a single thread drains
     * references at once.
     */
    private final AtomicBoolean draining = new AtomicBoolean();

    /**
     * Constructs a WeakLazyStripes where the concurrencyLevel
     * is the lower of either ConcurrentHashMap#DEFAULT_CONCURRENCY_LEVEL
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
     * @param concurrencyLevel The concurrency level for the underlying stripes map
     * @param creator A factory for creating new Stripes when needed
     */
    public WeakLazyStripes(final int concurrencyLevel, final Function<K, S> creator) {
        this(concurrencyLevel, creator, true);
    }

    /**
     * Constructs a WeakLazyStripes.
     *
     * @param concurrencyLevel The concurrency level for the underlying stripes map
     * @param creator A factory for creating new Stripes when needed
     * @param amortizeCleanup true if the cleanup of weak references should be
     *     amortized across many calls (default), false if the cleanup should be batched up
     *     and apportioned to a particular caller at a threshold
     */
    public WeakLazyStripes(final int concurrencyLevel, final Function<K, S> creator, final boolean amortizeCleanup) {
        this.stripes = new Object2ObjectOpenHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
        this.referenceQueue = new ReferenceQueue<>();
        this.creator = creator;
        this.amortizeCleanup = amortizeCleanup;
    }

    /**
     * Get the stripe for the given key
     *
     * If the stripe does not exist, it will be created by
     * calling {@link Function#apply(Object)} on {@link #creator}
     *
     * @param key the key for the stripe
     * @return the stripe
     */
    public S get(final K key) {

        final Holder<Boolean> written = new Holder<>(false);

        // 1) attempt lookup via optimistic read and immediate conversion to write lock
        WeakValueReference<K, S> stripeRef = getOptimistic(key, written);
        if (stripeRef == null) {

            // 2) attempt lookup via pessimistic read and immediate conversion to write lock
            stripeRef = getPessimistic(key, written);
            if (stripeRef == null) {

                // 3) attempt lookup via exclusive write lock
                stripeRef = getExclusive(key, written);
            }
        }

        if (amortizeCleanup) {
            if (written.value) {
                // TODO (AR) if we find that we are too frequently draining and it is expensive
                // then we could make the read and write drain paths both use the DRAIN_THRESHOLD
                drainClearedReferences();
            } else if (readCount.get() >= READ_DRAIN_THRESHOLD) {
                drainClearedReferences();
            }
        } else {
            // have we reached the threshold where we should clear
            // out any cleared WeakReferences from the stripes map
            final int count = expiredReferenceReadCount.get();
            if (count > MAX_EXPIRED_REFERENCE_READ_COUNT
                    && expiredReferenceReadCount.compareAndSet(count, 0)) {
                drainClearedReferences();
            }
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
     * Get the stripe via immediate conversion of an optimistic read lock to a write lock.
     *
     * @param key the stripe key
     * @param written (OUT) will be set to true if {@link #stripes} was updated
     *
     * @return null if we could not perform an optimistic read, or a new object needed to be
     *     created and we could not take the {@link #stripesLock} write lock immediately,
     *     otherwise the stripe.
     */
    private @Nullable WeakValueReference<K, S> getOptimistic(final K key, final Holder<Boolean> written) {
        // optimistic read
        final long stamp = stripesLock.tryOptimisticRead();
        WeakValueReference<K, S> stripeRef = stripes.get(key);
        if (stripeRef == null || stripeRef.get() == null) {
            final long writeStamp = stripesLock.tryConvertToWriteLock(stamp);
            if (writeStamp != 0l) {
                final boolean wasGCd = stripeRef != null && stripeRef.get() == null;
                try {
                    stripeRef = new WeakValueReference<>(key, creator.apply(key), referenceQueue);
                    stripes.put(key, stripeRef);
                } finally {
                    stripesLock.unlockWrite(writeStamp);
                }

                written.value = true;

                if (wasGCd && !amortizeCleanup) {
                    expiredReferenceReadCount.incrementAndGet();
                }
            }
        } else {
            if (stripesLock.validate(stamp)) {
                if (amortizeCleanup) {
                    readCount.incrementAndGet();
                }
            } else {
                // invalid optimistic read
                stripeRef = null;
            }
        }

        return stripeRef;
    }

    /**
     * Get the stripe via immediate conversion of a read lock to a write lock.
     *
     * @param key the stripe key
     * @param written (OUT) will be set to true if {@link #stripes} was updated
     *
     * @return null if a new object needed to be created and we could not take the {@link #stripesLock}
     *     write lock immediately, otherwise the stripe.
     */
    private @Nullable WeakValueReference<K, S> getPessimistic(final K key, final Holder<Boolean> written) {
        WeakValueReference<K, S> stripeRef;
        long stamp = stripesLock.readLock();
        try {
            stripeRef = stripes.get(key);
            if (stripeRef == null || stripeRef.get() == null) {
                final long writeStamp = stripesLock.tryConvertToWriteLock(stamp);
                if (writeStamp != 0l) {
                    final boolean wasGCd = stripeRef != null && stripeRef.get() == null;

                    stamp = writeStamp;  // NOTE: this causes the write lock to be released in the finally further down
                    stripeRef = new WeakValueReference<>(key, creator.apply(key), referenceQueue);
                    stripes.put(key, stripeRef);

                    written.value = true;

                    if (wasGCd && !amortizeCleanup) {
                        expiredReferenceReadCount.incrementAndGet();
                    }
                }

                return stripeRef;
            }
        } finally {
            stripesLock.unlock(stamp);
        }

        // else (we don't need the lock on this path)
        if (amortizeCleanup) {
            readCount.incrementAndGet();
        }

        return stripeRef;
    }

    /**
     * Get the stripe whilst holding the write lock.
     *
     * @param key the stripe key
     * @param written (OUT) will be set to true if {@link #stripes} was updated
     *
     * @return the stripe
     */
    private WeakValueReference<K, S> getExclusive(final K key, final Holder<Boolean> written) {
        WeakValueReference<K, S> stripeRef;
        final long writeStamp = stripesLock.writeLock();
        try {
            stripeRef = stripes.get(key);
            if (stripeRef == null || stripeRef.get() == null) {
                final boolean wasGCd = stripeRef != null && stripeRef.get() == null;

                stripeRef = new WeakValueReference<>(key, creator.apply(key), referenceQueue);
                stripes.put(key, stripeRef);

                written.value = true;

                if (wasGCd && !amortizeCleanup) {
                    expiredReferenceReadCount.incrementAndGet();
                }

                return stripeRef;
            }
        } finally {
            stripesLock.unlockWrite(writeStamp);
        }

        // else (we don't need the write lock on this path)
        if (amortizeCleanup) {
            readCount.incrementAndGet();
        }
        return stripeRef;
    }

    /**
     * Removes cleared WeakReferences
     * from the stripes map.
     *
     * If {@link #amortizeCleanup} is false, then
     * all cleared WeakReferences will be removed,
     * otherwise up to {@link #DRAIN_MAX} are removed.
     */
    private void drainClearedReferences() {
        if (draining.compareAndSet(false, true)) {  // critical section
            Reference<? extends S> ref;
            int i = 0;
            while ((ref = referenceQueue.poll()) != null) {
                @SuppressWarnings("unchecked") final WeakValueReference<K, S> stripeRef = (WeakValueReference<K, S>) ref;

                final long writeStamp = stripesLock.writeLock();
                try {
                    stripes.remove(stripeRef.key);
                } finally {
                    stripesLock.unlockWrite(writeStamp);
                }

                if (amortizeCleanup && ++i == DRAIN_MAX) {
                    break;
                }
            }
            if (amortizeCleanup) {
                readCount.set(0);
            }
            draining.set(false);
        }
    }

    /**
     * Extends a WeakReference with a strong reference to a key.
     *
     * Used for cleaning up the {@link #stripes} from the {@link #referenceQueue}.
     */
    private static class WeakValueReference<K, V> extends WeakReference<V> {
        final K key;
        public WeakValueReference(final K key, final V referent, final ReferenceQueue<? super V> q) {
            super(referent, q);
            this.key = key;
        }
    }
}
