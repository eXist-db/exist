/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

import static org.exist.storage.lock.LockTable.LockEventType.*;

/**
 * The Lock Table holds the details of
 * threads awaiting to acquire a Lock
 * and threads that have acquired a lock.
 *
 * It is arranged by the id of the lock
 * which is typically an indicator of the
 * lock subject.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LockTable {

    // org.exist.util.Configuration properties
    public static final String CONFIGURATION_DISABLED = "lock-table.disabled";
    public static final String CONFIGURATION_TRACE_STACK_DEPTH = "lock-table.trace-stack-depth";

    //TODO(AR) remove eventually!
    // legacy properties for overriding the config
    public static final String PROP_DISABLE = "exist.locktable.disable";
    public static final String PROP_TRACE_STACK_DEPTH = "exist.locktable.trace.stack.depth";

    private static final Logger LOG = LogManager.getLogger(LockTable.class);
    private static final String THIS_CLASS_NAME = LockTable.class.getName();

    /**
     * Set to false to disable all events
     */
    private final boolean disableEvents;

    /**
     * Whether we should try and trace the stack for the lock event, -1 means all stack,
     * 0 means no stack, n means n stack frames, 5 is a reasonable value
     */
    private int traceStackDepth;

    /**
     * Lock event listeners
     */
    private final StampedLock listenersLock = new StampedLock();
    @GuardedBy("listenersWriteLock") private volatile LockEventListener[] listeners = null;

    /**
     * Table of threads attempting to acquire a lock
     */
    private final Map<Thread, Entry> attempting = new ConcurrentHashMap<>(60);

    /**
     * Table of threads which have acquired lock(s)
     */
    private final Map<Thread, Entries> acquired = new ConcurrentHashMap<>(60);


    LockTable(final Configuration configuration) {
        this.disableEvents = LockManager.getLegacySystemPropertyOrConfigPropertyBool(PROP_DISABLE, configuration, CONFIGURATION_DISABLED, false);
        this.traceStackDepth = LockManager.getLegacySystemPropertyOrConfigPropertyInt(PROP_TRACE_STACK_DEPTH, configuration, CONFIGURATION_TRACE_STACK_DEPTH, 0);

        // add a log listener if trace level logging is enabled
        if(LOG.isTraceEnabled()) {
            registerListener(new LockEventLogListener(LOG, Level.TRACE));
        }
    }

    /**
     * Shuts down the lock table processor.
     *
     * After calling this, no further lock
     * events will be reported.
     */
    public void shutdown() {
    }

    /**
     * Set the depth at which we should trace lock events through the stack
     *
     * @param traceStackDepth -1 traces the whole stack, 0 means no stack traces, n means n stack frames
     */
    public void setTraceStackDepth(final int traceStackDepth) {
        this.traceStackDepth = traceStackDepth;
    }

    public void attempt(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(Attempt, groupId, id, lockType, mode);
    }

    public void attemptFailed(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(AttemptFailed, groupId, id, lockType, mode);
    }

    public void acquired(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(Acquired, groupId, id, lockType, mode);
    }

    public void released(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(Released, groupId, id, lockType, mode);
    }

    private void event(final LockEventType lockEventType, final long groupId, final String id, final LockType lockType, final LockMode lockMode) {
        if(disableEvents) {
            return;
        }

        final long timestamp = System.nanoTime();
        final Thread currentThread = Thread.currentThread();

//        if(ignoreEvent(threadName, id)) {
//            return;
//        }

//        /**
//         * Very useful for debugging Lock life cycles
//         */
//        if (sanityCheck) {
//            sanityCheckLockLifecycles(lockEventType, groupId, id, lockType, lockMode, threadName, 1, timestamp, stackTrace);
//        }

        switch (lockEventType) {
            case Attempt:

                // happens once per thread!
                Entry entry = attempting.computeIfAbsent(currentThread, k -> new Entry());

                entry.id = id;
                entry.lockType = lockType;
                entry.lockMode = lockMode;
                entry.owner = currentThread.getName();
                if(traceStackDepth == 0) {
                    entry.stackTraces = null;
                } else {
                    entry.stackTraces = new ArrayList<>();
                    entry.stackTraces.add(getStackTrace(currentThread));
                }
                // write count last to ensure reader-thread visibility of above fields
                entry.count = 1;

                notifyListeners(lockEventType, timestamp, groupId, entry);

                break;


            case AttemptFailed:
                final Entry attemptFailedEntry = attempting.get(currentThread);
                if (attemptFailedEntry == null || attemptFailedEntry.count == 0) {
                    LOG.error("No entry found when trying to remove failed `attempt` for: id={}, thread={}", id, currentThread.getName());
                    break;
                }

                // mark attempt as unused
                attemptFailedEntry.count = 0;

                notifyListeners(lockEventType, timestamp, groupId, attemptFailedEntry);

                break;


            case Acquired:
                final Entry attemptEntry = attempting.get(currentThread);
                if (attemptEntry == null || attemptEntry.count == 0) {
                    LOG.error("No entry found when trying to remove `attempt` to promote to `acquired` for: id={}, thread={}", id, currentThread.getName());

                    break;
                }

                // we now either add or merge the `attemptEntry` with the `acquired` table
                Entries acquiredEntries = acquired.get(currentThread);

                if (acquiredEntries == null) {
                    final Entry acquiredEntry = new Entry(attemptEntry);

                    acquiredEntries = new Entries(acquiredEntry);
                    acquired.put(currentThread, acquiredEntries);

                    notifyListeners(lockEventType, timestamp, groupId, acquiredEntry);

                } else {

                    final Entry acquiredEntry = acquiredEntries.merge(attemptEntry);
                    notifyListeners(lockEventType, timestamp, groupId, acquiredEntry);
                }

                // mark attempt as unused
                attemptEntry.count = 0;

                break;


            case Released:
                final Entries entries = acquired.get(currentThread);
                if (entries == null) {
                    LOG.error("No entries found when trying to `release` for: id={}, thread={}", id, currentThread.getName());
                    break;
                }

                final Entry releasedEntry = entries.unmerge(id, lockType, lockMode);
                if (releasedEntry == null) {
                    LOG.error("Unable to unmerge entry for `release`: id={}, threadName={}", id, currentThread.getName());
                    break;
                }

                notifyListeners(lockEventType, timestamp, groupId, releasedEntry);

                break;
        }
    }

    /**
     * There is one Entries object for each writing-thread,
     * however it may be read from other threads which
     * is why it needs to be thread-safe.
     */
    @ThreadSafe
    private static class Entries {
        private final StampedLock entriesLock = new StampedLock();
        @GuardedBy("entriesLock") private final ObjectLinkedOpenHashSet<Entry> entries = new ObjectLinkedOpenHashSet<>();

        public Entries(final Entry entry) {
            entries.add(entry);
        }

        public Entry merge(final Entry attemptEntry) {
            // try optimistic read
            long optReadStamp = entriesLock.tryOptimisticRead();
            long readStamp = -1;
            long stamp = -1;
            try {

                Entry local = entries.get(attemptEntry);
                if (!entriesLock.validate(optReadStamp)) {

                    // otherwise... pessimistic read
                    readStamp = entriesLock.readLock();
                    stamp = readStamp;
                    local = entries.get(attemptEntry);
                } else {
                    stamp = optReadStamp;
                }

                // if found, do the merge
                if (local != null) {
                    if (attemptEntry.stackTraces != null) {
                        local.stackTraces.addAll(attemptEntry.stackTraces);
                    }
                    local.count += attemptEntry.count;
                    return local;
                }

                // try to upgrade optimistic-read or read lock to write lock
                stamp = entriesLock.tryConvertToWriteLock(stamp);
                if (stamp == 0L) {

                    // failed to upgrade
                    if (readStamp != -1) {
                        // release the read lock before taking the write lock
                        entriesLock.unlockRead(readStamp);
                    }

                    // take the write lock (blocking)
                    stamp = entriesLock.writeLock();

                    // we must refresh the `local` as it could have changed between releasing the readLock and obtaining the write lock
                    local = entries.get(attemptEntry);

                    // if found, do the merge
                    if (local != null) {
                        if (attemptEntry.stackTraces != null) {
                            local.stackTraces.addAll(attemptEntry.stackTraces);
                        }
                        local.count += attemptEntry.count;
                        return local;
                    }
                }

                // we have a write lock, add it
                final Entry acquiredEntry = new Entry(attemptEntry);
                entries.add(acquiredEntry);
                return acquiredEntry;
            } finally {
                // we don't need to unlock if it was just an optimistic read
                if (stamp != optReadStamp) {
                    entriesLock.unlock(stamp);
                }
            }
        }

        @Nullable
        public Entry unmerge(final String id, final LockType lockType, final LockMode lockMode) {
            final Entry key = new Entry(id, lockType, lockMode, null, null);

            // optimistic read
            long stamp = entriesLock.tryOptimisticRead();
            Entry local = entries.get(key);
            // if count is equal to 1 we can just remove from the list rather than decrementing
            if (local.count == 1) {
                final long writeStamp = entriesLock.tryConvertToWriteLock(stamp);
                if (writeStamp != 0L) {
                    try {
                        entries.remove(local);
                        local.count--;
                        return local;
                    } finally {
                        entriesLock.unlockWrite(writeStamp);
                    }
                }
            } else {
                if (entriesLock.validate(stamp)) {

                    // do the unmerge bit
                    if (local.stackTraces != null) {
                        local.stackTraces.removeLast();
                    }
                    local.count = local.count - 1;

                    //done
                    return local;
                }
            }


            // otherwise... pessimistic read
            boolean mustRemove;
            stamp = entriesLock.readLock();
            try {

                local = entries.get(key);

                // if count is equal to 1 we can just remove from the list rather than decrementing
                if (local.count == 1) {

                    final long writeStamp = entriesLock.tryConvertToWriteLock(stamp);
                    if (writeStamp != 0L) {
                        stamp = writeStamp;  // NOTE: this causes the write lock to be released in the finally further down
                        entries.remove(local);
                        local.count--;
                        return local;
                    }

                } else {
                    // do the unmerge bit
                    if (local.stackTraces != null) {
                        local.stackTraces.removeLast();
                    }
                    local.count = local.count - 1;

                    //done
                    return local;
                }

                mustRemove = true;
            } finally {
                entriesLock.unlock(stamp);
            }

            // unable to remove by tryConvertToWriteLock above, so directly acquire write lock
            if (mustRemove) {
                stamp = entriesLock.writeLock();
                try {
                    entries.remove(local);
                    local.count--;
                    return local;
                } finally {
                    entriesLock.unlockWrite(stamp);
                }
            }

            return null;
        }

        public void forEach(final Consumer<Entry> entryConsumer) {
            final long stamp = entriesLock.readLock();
            try {
                entries.forEach(entryConsumer);
            } finally {
                entriesLock.unlockRead(stamp);
            }
        }
    }

    @Nullable
    private StackTraceElement[] getStackTrace(final Thread thread) {
        final StackTraceElement[] stackTrace = thread.getStackTrace();
        final int lastStackTraceElementIdx = stackTrace.length - 1;

        final int from = findFirstExternalFrame(stackTrace);
        final int to;
        if (traceStackDepth == -1) {
            to = lastStackTraceElementIdx;
        } else {
            final int calcTo = from + traceStackDepth;
            if (calcTo > lastStackTraceElementIdx) {
                to = lastStackTraceElementIdx;
            } else {
                to = calcTo;
            }
        }

        return Arrays.copyOfRange(stackTrace, from, to);
    }

    private int findFirstExternalFrame(final StackTraceElement[] stackTrace) {
        // we start with i = 1 to avoid Thread#getStackTrace() frame
        for(int i = 1; i < stackTrace.length; i++) {
            if(!THIS_CLASS_NAME.equals(stackTrace[i].getClassName())) {
                return i;
            }
        }
        return 0;
    }

    public void registerListener(final LockEventListener lockEventListener) {
        final long stamp = listenersLock.writeLock();
        try {
            // extend listeners by 1
            if (listeners == null) {
                listeners = new LockEventListener[1];
                listeners[0] = lockEventListener;
            } else {
                final LockEventListener[] newListeners = new LockEventListener[listeners.length + 1];
                System.arraycopy(listeners, 0, newListeners, 0, listeners.length);
                newListeners[listeners.length] = lockEventListener;
                listeners = newListeners;
            }
        } finally {
            listenersLock.unlockWrite(stamp);
        }

        lockEventListener.registered();
    }

    public void deregisterListener(final LockEventListener lockEventListener) {
        final long stamp = listenersLock.writeLock();
        try {
            // reduce listeners by 1
            for (int i = listeners.length - 1; i > -1; i--) {
                // intentionally compare by identity!
                if (listeners[i] == lockEventListener) {

                    if (i == 0 && listeners.length == 1) {
                        listeners = null;
                        break;
                    }

                    final LockEventListener[] newListeners = new LockEventListener[listeners.length - 1];
                    System.arraycopy(listeners, 0, newListeners, 0, i);
                    if (listeners.length != i) {
                        System.arraycopy(listeners, i + 1, newListeners, i, listeners.length - i - 1);
                    }
                    listeners = newListeners;

                    break;
                }
            }
        } finally {
            listenersLock.unlockWrite(stamp);
        }

        lockEventListener.unregistered();
    }

    /**
     * Get's a copy of the current lock attempt information
     *
     * @return lock attempt information
     */
    public Map<String, Map<LockType, List<LockModeOwner>>> getAttempting() {
        final Map<String, Map<LockType, List<LockModeOwner>>> result = new HashMap<>();

        for (Entry entry : attempting.values()) {
            // read count (volatile) first to ensure visibility
            final int localCount = entry.count;
            if (localCount == 0) {
                // attempt entry object is marked as unused
                continue;
            }

            result.compute(entry.id, (_k, v) -> {
                if (v == null) {
                    v = new HashMap<>();
                }

                v.compute(entry.lockType, (_k1, v1) -> {
                    if (v1 == null) {
                        v1 = new ArrayList<>();
                    }
                    v1.add(new LockModeOwner(entry.lockMode, entry.owner, entry.stackTraces != null ? entry.stackTraces.getFirst() : null));
                    return v1;
                });

                return v;
            });
        }

        return result;
    }

    /**
     * Get's a copy of the current acquired lock information
     *
     * @return acquired lock information
     */
    public Map<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> getAcquired() {
        final Map<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> result = new HashMap<>();

        for (Entries entries : acquired.values()) {
            entries.forEach(entry -> {

                // read count (volatile) first to ensure visibility
                final int localCount = entry.count;

                result.compute(entry.id, (_k, v) -> {
                    if (v == null) {
                        v = new EnumMap<>(LockType.class);
                    }

                    v.compute(entry.lockType, (_k1, v1) -> {
                        if (v1 == null) {
                            v1 = new EnumMap<>(LockMode.class);
                        }

                        v1.compute(entry.lockMode, (_k2, v2) -> {
                            if (v2 == null) {
                                v2 = new HashMap<>();
                            }

                            v2.compute(entry.owner, (_k3, v3) -> {
                                if (v3 == null) {
                                    v3 = new LockCountTraces(localCount, entry.stackTraces);
                                } else {
                                    v3.count += localCount;
                                    if (entry.stackTraces != null) {
                                        v3.traces.addAll(entry.stackTraces);
                                    }
                                }

                                return v3;
                            });

                            return v2;

                        });

                        return v1;
                    });

                    return v;
                });
            });
        }

        return result;
    }

    public static class LockModeOwner {
        final LockMode lockMode;
        final String ownerThread;
        @Nullable final StackTraceElement[] trace;

        public LockModeOwner(final LockMode lockMode, final String ownerThread, @Nullable final StackTraceElement[] trace) {
            this.lockMode = lockMode;
            this.ownerThread = ownerThread;
            this.trace = trace;
        }

        public LockMode getLockMode() {
            return lockMode;
        }

        public String getOwnerThread() {
            return ownerThread;
        }

        @Nullable public StackTraceElement[] getTrace() {
            return trace;
        }
    }

    public static class LockCountTraces {
        int count;
        @Nullable final List<StackTraceElement[]> traces;

        public LockCountTraces(final int count, @Nullable final List<StackTraceElement[]> traces) {
            this.count = count;
            this.traces = traces;
        }

        public int getCount() {
            return count;
        }

        @Nullable
        public List<StackTraceElement[]> getTraces() {
            return traces;
        }
    }

    private void notifyListeners(final LockEventType lockEventType, final long timestamp, final long groupId,
            final Entry entry) {
        if (listeners == null) {
            return;
        }

        final long stamp = listenersLock.readLock();
        try {
            for (LockEventListener listener : listeners) {
                try {
                    listener.accept(lockEventType, timestamp, groupId, entry);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        } finally {
            listenersLock.unlockRead(stamp);
        }
    }

    private static @Nullable <T> List<T> List(@Nullable final T item) {
        if (item == null) {
            return null;
        }

        final List<T> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    public interface LockEventListener {
        default void registered() {}
        void accept(final LockEventType lockEventType, final long timestamp, final long groupId, final Entry entry);
        default void unregistered() {}
    }

    public enum LockEventType {
        Attempt,
        AttemptFailed,
        Acquired,
        Released
    }

    public static String formatString(final LockEventType lockEventType, final long groupId, final String id,
            final LockType lockType, final LockMode lockMode, final String threadName, final int count,
            final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
        final StringBuilder builder = new StringBuilder()
                .append(lockEventType.name())
                .append(' ')
                .append(lockType.name());

        if(groupId > -1) {
            builder
                    .append("#")
                    .append(groupId);
        }

        builder.append('(')
                .append(lockMode.toString())
                .append(") of ")
                .append(id);

        if(stackTrace != null) {
            final String reason = getSimpleStackReason(stackTrace);
            if(reason != null) {
                builder
                        .append(" for #")
                        .append(reason);
            }
        }

        builder
                .append(" by ")
                .append(threadName)
                .append(" at ")
                .append(timestamp);

        if (lockEventType == Acquired || lockEventType == Released) {
            builder
                    .append(". count=")
                    .append(count);
        }

        return builder.toString();
    }

    private static final String NATIVE_BROKER_CLASS_NAME = NativeBroker.class.getName();
    private static final String COLLECTION_STORE_CLASS_NAME = NativeBroker.class.getName();
    private static final String TXN_CLASS_NAME = Txn.class.getName();

    @Nullable
    public static String getSimpleStackReason(final StackTraceElement[] stackTrace) {
        for (final StackTraceElement stackTraceElement : stackTrace) {
            final String className = stackTraceElement.getClassName();

            if (className.equals(NATIVE_BROKER_CLASS_NAME) || className.equals(COLLECTION_STORE_CLASS_NAME) || className.equals(TXN_CLASS_NAME)) {
                if (!(stackTraceElement.getMethodName().endsWith("LockCollection") || "lockCollectionCache".equals(stackTraceElement.getMethodName()))) {
                    return stackTraceElement.getMethodName() + '(' + stackTraceElement.getLineNumber() + ')';
                }
            }
        }

        return null;
    }

    /**
     * Represents an entry in the {@link #attempting} or {@link #acquired} lock table.
     *
     * All class members are only written from a single
     * thread.
     *
     * However, they may be read from the same writer thread or a different read-only thread.
     * The member `count` is written last by the writer thread
     * and read first by the read-only reader thread to ensure correct visibility
     * of the member values.
     */
    public static class Entry {
        String id;
        LockType lockType;
        LockMode lockMode;
        String owner;

        @Nullable List<StackTraceElement[]> stackTraces;

        /**
         * Intentionally marked volatile.
         * All variables visible before this point become available
         * to the reading thread.
         */
        volatile int count = 0;

        private Entry() {
        }

        private Entry(final String id, final LockType lockType, final LockMode lockMode, final String owner,
                @Nullable final StackTraceElement[] stackTrace) {
            this.id = id;
            this.lockType = lockType;
            this.lockMode = lockMode;
            this.owner = owner;
            if (stackTrace != null) {
                this.stackTraces = new ArrayList<>();
                this.stackTraces.add(stackTrace);
            } else {
                this.stackTraces = null;
            }
            // write last to ensure reader visibility of above fields!
            this.count = 1;
        }

        private Entry(final Entry other) {
            this.id = other.id;
            this.lockType = other.lockType;
            this.lockMode = other.lockMode;
            this.owner = other.owner;
            if (other.stackTraces != null) {
                this.stackTraces = (ArrayList)((ArrayList)other.stackTraces).clone();
            } else {
                this.stackTraces = null;
            }
            // write last to ensure reader visibility of above fields!
            this.count = other.count;
        }

        public void setFrom(final Entry entry) {
            this.id = entry.id;
            this.lockType = entry.lockType;
            this.lockMode = entry.lockMode;
            this.owner = entry.owner;
            if (entry.stackTraces != null) {
                this.stackTraces = new ArrayList<>(entry.stackTraces);
            } else {
                this.stackTraces = null;
            }
            // write last to ensure reader visibility of above fields!
            this.count = entry.count;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || Entry.class != o.getClass()) return false;
            Entry entry = (Entry) o;
            return lockType == entry.lockType &&
                    lockMode == entry.lockMode
                    && id.equals(entry.id);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + lockType.hashCode();
            result = 31 * result + lockMode.hashCode();
            return result;
        }

        public String getId() {
            return id;
        }

        public LockType getLockType() {
            return lockType;
        }

        public LockMode getLockMode() {
            return lockMode;
        }

        public String getOwner() {
            return owner;
        }

        @Nullable
        public List<StackTraceElement[]> getStackTraces() {
            return stackTraces;
        }

        public int getCount() {
            return count;
        }
    }


    /** debugging tools below **/

//    public static final String PROP_SANITY_CHECK = "exist.locktable.sanity.check";
//
//    /**
//     * Set to true to enable sanity checking of lock leases
//     */
//    private volatile boolean sanityCheck = Boolean.getBoolean(PROP_SANITY_CHECK);
//
//    /**
//     * Holds a count of READ and WRITE locks by {@link Entry#id}
//     * Only used for debugging,see {@link #sanityCheckLockLifecycles(LockEventType, long, String, LockType,
//     *     LockMode, String, int, long, StackTraceElement[])}.
//     */
//    @GuardedBy("this") private final Map<String, Tuple2<Long, Long>> lockCounts = new HashMap<>();
//
//    /**
//     * Checks that there are not more releases that there are acquires
//     */
//    private void sanityCheckLockLifecycles(final LockEventType lockEventType, final long groupId, final String id,
//            final LockType lockType, final LockMode lockMode, final String threadName, final int count,
//            final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
//        synchronized(lockCounts) {
//            long read = 0;
//            long write = 0;
//
//            final Tuple2<Long, Long> lockCount = lockCounts.get(id);
//            if(lockCount != null) {
//                read = lockCount._1;
//                write = lockCount._2;
//            }
//
//            if(lockEventType == Acquired) {
//                if(lockMode == LockMode.READ_LOCK) {
//                    read++;
//                } else if(lockMode == LockMode.WRITE_LOCK) {
//                    write++;
//                }
//            } else if(lockEventType == Released) {
//                if(lockMode == LockMode.READ_LOCK) {
//                    if(read == 0) {
//                        LOG.error("Negative READ_LOCKs", new IllegalStateException());
//                    }
//                    read--;
//                } else if(lockMode == LockMode.WRITE_LOCK) {
//                    if(write == 0) {
//                        LOG.error("Negative WRITE_LOCKs", new IllegalStateException());
//                    }
//                    write--;
//                }
//            }
//
//            if(LOG.isTraceEnabled()) {
//                LOG.trace("QUEUE: {} (read={} write={})", formatString(lockEventType, groupId, id, lockType, lockMode,
//                        threadName, count, timestamp, stackTrace), read, write);
//            }
//
//            lockCounts.put(id, Tuple(read, write));
//        }
//    }

//    /**
//     * Simple filtering to ignore events that are not of interest
//     *
//     * @param threadName The name of the thread that triggered the event
//     * @param id The id of the lock
//     *
//     * @return true if the event should be ignored
//     */
//    private boolean ignoreEvent(final String threadName, final String id) {
//        // useful for debugging specific log events
//        return threadName.startsWith("DefaultQuartzScheduler_")
//                || id.equals("dom.dbx")
//                || id.equals("collections.dbx")
//                || id.equals("collections.dbx")
//                || id.equals("structure.dbx")
//                || id.equals("values.dbx")
//                || id.equals("CollectionCache");
//    }
}
