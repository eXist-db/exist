/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2017 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.txn.Txn;
import org.exist.util.RingBuffer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.concurrent.*;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
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
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTable {

    public static final String PROP_DISABLE = "exist.locktable.disable";
    public static final String PROP_SANITY_CHECK = "exist.locktable.sanity.check";
    public static final String PROP_TRACE_STACK_DEPTH = "exist.locktable.trace.stack.depth";

    private static final Logger LOG = LogManager.getLogger(LockTable.class);
    private static final String THIS_CLASS_NAME = LockTable.class.getName();

    /**
     * Set to false to disable all events
     */
    private volatile boolean disableEvents = Boolean.getBoolean(PROP_DISABLE);

    /**
     * Set to true to enable sanity checking of lock leases
     */
    private volatile boolean sanityCheck = Boolean.getBoolean(PROP_SANITY_CHECK);

    /**
     * Whether we should try and trace the stack for the lock event, -1 means all stack,
     * 0 means no stack, n means n stack frames, 5 is a reasonable value
     */
    private volatile int traceStackDepth = Optional.ofNullable(Integer.getInteger(PROP_TRACE_STACK_DEPTH))
            .orElse(0);

    /**
     * Lock event listeners
     */
    private final List<LockEventListener> listeners = new CopyOnWriteArrayList<>();

    // thread local object pools
    private static final ThreadLocal<RingBuffer<char[]>> THREADLOCAL_CHAR_ARRAY_POOL = ThreadLocal.withInitial(() -> new RingBuffer<>(24, () -> new char[42]));
    private static final ThreadLocal<RingBuffer<EntryKey>> THREADLOCAL_ENTRY_KEY_POOL = ThreadLocal.withInitial(() -> new RingBuffer<>(24, EntryKey::new));
    private static final ThreadLocal<RingBuffer<Entry>> THREADLOCAL_ENTRY_POOL = ThreadLocal.withInitial(() -> new RingBuffer<>(12, Entry::new));

    /**
     * List of threads attempting to acquire a lock
     *
     * Map<Id, Map<Lock Type, List<LockModeOwner>>>
     */
    private final Map<EntryKey, Entry> attempting = new ConcurrentHashMap<>();

    /**
     * Reference count of acquired locks by id and type
     *
     * Map<Id, Map<Lock Type, Map<Lock Mode, Map<Owner, LockCountTraces>>>>
     */
    private final Map<EntryKey, Entry> acquired = new ConcurrentHashMap<>();

    /**
     * Holds a count of READ and WRITE locks by {@link Entry#id}
     * Only used for debugging,see {@link #sanityCheckLockLifecycles(LockEventType, long, String, LockType,
     *     LockMode, String, int, long, StackTraceElement[])}.
     */
    @GuardedBy("this") private final Map<String, Tuple2<Long, Long>> lockCounts = new HashMap<>();


    LockTable() {
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

        final Thread currentThread = Thread.currentThread();
        final String threadName = currentThread.getName();
        final long threadId = currentThread.getId();

        if(ignoreEvent(threadName, id)) {
            return;
        }

        final long timestamp = System.nanoTime();

        @Nullable final StackTraceElement[] stackTrace;
        if(traceStackDepth == 0) {
            stackTrace = null;
        } else {
            stackTrace = getStackTrace(currentThread);
        }

        /**
         * Very useful for debugging Lock life cycles
         */
        if (sanityCheck) {
            sanityCheckLockLifecycles(lockEventType, groupId, id, lockType, lockMode, threadName, 1, timestamp, stackTrace);
        }

        switch (lockEventType) {
            case Attempt:
                Entry entry = THREADLOCAL_ENTRY_POOL.get().takeEntry();
                if (entry == null) {
                    entry = new Entry();
                }
                entry.id = id;
                entry.lockType = lockType;
                entry.lockMode = lockMode;
                entry.owner = threadName;
                if (stackTrace != null) {
                    entry.stackTraces = new ArrayList<>();
                    entry.stackTraces.add(stackTrace);
                } else {
                    entry.stackTraces = null;
                }
                // write count last to ensure reader-thread visibility of above fields
                entry.count = 1;

                final EntryKey entryKey = key(threadId, id, lockType, lockMode);
                entry.entryKey = entryKey;

                notifyListeners(lockEventType, timestamp, groupId, entry);

                attempting.put(entryKey, entry);
                break;


            case AttemptFailed:
                final EntryKey attemptFailedEntryKey = key(threadId, id, lockType, lockMode);
                final Entry attemptFailedEntry = attempting.remove(attemptFailedEntryKey);
                if (attemptFailedEntry == null) {
                    LOG.error("No entry found when trying to remove failed attempt for: id={}" + id);

                } else {
                    notifyListeners(lockEventType, timestamp, groupId, attemptFailedEntry);

                    // release the key in the map
                    THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(attemptFailedEntry.entryKey);
                    THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(attemptFailedEntry.entryKey.buf);

                    // release the value in the map
                    THREADLOCAL_ENTRY_POOL.get().returnEntry(attemptFailedEntry);
                }

                // release the key that we used for the lookup
                THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(attemptFailedEntryKey);
                THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(attemptFailedEntryKey.buf);

                break;


            case Acquired:
                final EntryKey attemptEntryKey = key(threadId, id, lockType, lockMode);
                final Entry attemptEntry = attempting.remove(attemptEntryKey);
                if (attemptEntry == null) {
                    LOG.error("No entry found when trying to remove acquired attempt for: id={}" + id);
                    attempting.remove(attemptEntryKey);

                    // release the key that we used for the lookup
                    THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(attemptEntryKey);
                    THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(attemptEntryKey.buf);
                    break;
                }

                // release the key that we used for the lookup
                THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(attemptEntryKey);
                THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(attemptEntryKey.buf);

                // we now either add or merge the `attemptEntry` with the `acquired` table
                 Entry acquiredEntry = acquired.get(attemptEntry.entryKey);
                if (acquiredEntry == null) {
                    acquired.put(attemptEntry.entryKey, attemptEntry);
                    acquiredEntry = attemptEntry;
                } else {
                    if (attemptEntry.stackTraces != null) {
                        acquiredEntry.stackTraces.addAll(attemptEntry.stackTraces);
                    }
                    acquiredEntry.count += attemptEntry.count;

                    // release the attempt entry (as we merged, rather than added)
                    THREADLOCAL_ENTRY_POOL.get().returnEntry(attemptEntry);
                    THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(attemptEntry.entryKey);
                    THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(attemptEntry.entryKey.buf);
                }

                notifyListeners(lockEventType, timestamp, groupId, acquiredEntry);

                break;


            case Released:
                final EntryKey acquiredEntryKey = key(threadId, id, lockType, lockMode);

                final Entry releasedEntry = acquired.get(acquiredEntryKey);
                if (releasedEntry == null) {
                    LOG.error("No entry found when trying to release for: id={}" + id);

                    // release the key that we used for the lookup
                    THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(acquiredEntryKey);
                    THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(acquiredEntryKey.buf);
                    break;
                }

                // release the key that we used for the lookup
                THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(acquiredEntryKey);
                THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(acquiredEntryKey.buf);

                final int localCount = releasedEntry.count;

                // decrement
                if (releasedEntry.stackTraces != null) {
                    releasedEntry.stackTraces.remove(releasedEntry.stackTraces.size() - 1);
                }
                releasedEntry.count = localCount - 1;

                notifyListeners(lockEventType, timestamp, groupId, releasedEntry);

                if (releasedEntry.count == 0) {
                    // remove the entry
                    if (acquired.remove(releasedEntry.entryKey) == null) {
                        LOG.error("Unable to remove entry for: id={}" + id);
                    }

                    // release the entry
                    THREADLOCAL_ENTRY_POOL.get().returnEntry(releasedEntry);
                    THREADLOCAL_ENTRY_KEY_POOL.get().returnEntry(releasedEntry.entryKey);
                    THREADLOCAL_CHAR_ARRAY_POOL.get().returnEntry(releasedEntry.entryKey.buf);
                }

                break;
        }
    }

    /**
     * Simple filtering to ignore events that are not of interest
     *
     * @param threadName The name of the thread that triggered the event
     * @param id The id of the lock
     *
     * @return true if the event should be ignored
     */
    private boolean ignoreEvent(final String threadName, final String id) {
        return false;

        // useful for debugging specific log events
//        return threadName.startsWith("DefaultQuartzScheduler_")
//                || id.equals("dom.dbx")
//                || id.equals("collections.dbx")
//                || id.equals("collections.dbx")
//                || id.equals("structure.dbx")
//                || id.equals("values.dbx")
//                || id.equals("CollectionCache");
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
        listeners.add(lockEventListener);
        lockEventListener.registered();
    }

    public void deregisterListener(final LockEventListener lockEventListener) {
        listeners.remove(lockEventListener);
        lockEventListener.unregistered();
    }

    /**
     * Get's a copy of the current lock attempt information
     *
     * @return lock attempt information
     */
    public Map<String, Map<LockType, List<LockModeOwner>>> getAttempting() {
        final Map<String, Map<LockType, List<LockModeOwner>>> result = new HashMap<>();

        final Iterator<Entry> it = attempting.values().iterator();
        while (it.hasNext()) {
            final Entry entry = it.next();

            // read count (volatile) first to ensure visibility
            final int localCount = entry.count;

            result.compute(entry.id, (_k, v) -> {
                if (v == null) {
                    v = new HashMap<>();
                }

                v.compute(entry.lockType, (_k1, v1) -> {
                    if (v1 == null) {
                        v1 = new ArrayList<>();
                    }
                    v1.add(new LockModeOwner(entry.lockMode, entry.owner));
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

        final Iterator<Entry> it = acquired.values().iterator();
        while (it.hasNext()) {
            final Entry entry = it.next();

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
        }

        return result;
    }

    public static class LockModeOwner {
        final LockMode lockMode;
        final String ownerThread;

        public LockModeOwner(final LockMode lockMode, final String ownerThread) {
            this.lockMode = lockMode;
            this.ownerThread = ownerThread;
        }

        public LockMode getLockMode() {
            return lockMode;
        }

        public String getOwnerThread() {
            return ownerThread;
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

    private void notifyListeners(final LockEventType lockEventType, final long timestamp, final long groupId, final Entry entry) {
        for (final LockEventListener listener : listeners) {
            try {
                listener.accept(lockEventType, timestamp, groupId, entry);
            } catch (final Exception e) {
                LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
            }
        }
    }

//        private void notifyListenersOfAcquire(final LockAction lockAction, final int newReferenceCount) {
//            final LockAction newLockAction = lockAction.withCount(newReferenceCount);
//            for(final LockEventListener listener : listeners) {
//                try {
//                    listener.accept(newLockAction);
//                } catch (final Exception e) {
//                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
//                }
//            }
//        }

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

    /** debugging tools below **/

    /**
     * Checks that there are not more releases that there are acquires
     */
    private void sanityCheckLockLifecycles(final LockEventType lockEventType, final long groupId, final String id,
            final LockType lockType, final LockMode lockMode, final String threadName, final int count,
            final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
        synchronized(lockCounts) {
            long read = 0;
            long write = 0;

            final Tuple2<Long, Long> lockCount = lockCounts.get(id);
            if(lockCount != null) {
                read = lockCount._1;
                write = lockCount._2;
            }

            if(lockEventType == Acquired) {
                if(lockMode == LockMode.READ_LOCK) {
                    read++;
                } else if(lockMode == LockMode.WRITE_LOCK) {
                    write++;
                }
            } else if(lockEventType == Released) {
                if(lockMode == LockMode.READ_LOCK) {
                    if(read == 0) {
                        LOG.error("Negative READ_LOCKs", new IllegalStateException());
                    }
                    read--;
                } else if(lockMode == LockMode.WRITE_LOCK) {
                    if(write == 0) {
                        LOG.error("Negative WRITE_LOCKs", new IllegalStateException());
                    }
                    write--;
                }
            }

            if(LOG.isTraceEnabled()) {
                LOG.trace("QUEUE: {} (read={} write={})", formatString(lockEventType, groupId, id, lockType, lockMode,
                        threadName, count, timestamp, stackTrace), read, write);
            }

            lockCounts.put(id, Tuple(read, write));
        }
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
                    .append(Integer.toString(count));
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
                if (!(stackTraceElement.getMethodName().endsWith("LockCollection") || stackTraceElement.getMethodName().equals("lockCollectionCache"))) {
                    return stackTraceElement.getMethodName() + '(' + stackTraceElement.getLineNumber() + ')';
                }
            }
        }

        return null;
    }

    private static EntryKey key(final long threadId, final String id, final LockType lockType, final LockMode lockMode) {
        final boolean idIsUri = lockType == LockType.COLLECTION || lockType == LockType.DOCUMENT;

        final int requiredLen = 8 + 1 + (id.length() - (idIsUri ? (id.equals("/db") ? 3 : 4) : 0));

        char[] buf = THREADLOCAL_CHAR_ARRAY_POOL.get().takeEntry();
        if (buf == null || buf.length < requiredLen) {
            buf = new char[requiredLen];
        }

        longToChar(threadId, buf);
        buf[8] = (char) ((lockMode.getVal() << 4) | lockType.getVal());

        if (idIsUri) {
            appendUri(buf, 9, requiredLen, id);
        } else {
            id.getChars(0, id.length(), buf, 9);
        }

        EntryKey key = THREADLOCAL_ENTRY_KEY_POOL.get().takeEntry();
        if (key == null) {
            key = new EntryKey();
        }
        key.setBuf(buf, requiredLen);

        return key;
    }

    private static void longToChar(final long v, final char[] data) {
        data[0] = (char) ((v >>> 0) & 0xff);
        data[1] = (char) ((v >>> 8) & 0xff);
        data[2] = (char) ((v >>> 16) & 0xff);
        data[3] = (char) ((v >>> 24) & 0xff);
        data[4] = (char) ((v >>> 32) & 0xff);
        data[5] = (char) ((v >>> 40) & 0xff);
        data[6] = (char) ((v >>> 48) & 0xff);
        data[7] = (char) ((v >>> 56) & 0xff);
    }

    private static void appendUri(final char[] buf, int bufOffset, final int bufLen, final String id) {
        int partEnd = id.length() - 1;
        for (int i = partEnd; bufOffset < bufLen; i--) {
            final char c = id.charAt(i);
            if (c == '/') {
                id.getChars(i + 1, partEnd + 1, buf, bufOffset);
                bufOffset += partEnd - i;
                partEnd = i - 1;
                if (bufOffset < bufLen) {
                    buf[bufOffset++] = '/';
                }
            }
        }
    }

    private static class EntryKey {
        private char[] buf;
        private int bufLen;
        private int hashCode;

        public void setBuf(final char buf[], final int bufLen) {
            this.buf = buf;
            this.bufLen = bufLen;

            // calculate hashcode
            hashCode = 1;
            for (int i = 0; i < bufLen; i++)
                hashCode = 31 * hashCode + buf[i];
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || EntryKey.class != o.getClass()) return false;

            final EntryKey other = ((EntryKey) o);

            if (buf == other.buf)
                return true;

            if (other.bufLen != bufLen)
                return false;

            for (int i = 0; i < bufLen; i++)
                if (buf[i] != other.buf[i])
                    return false;

            return true;
        }
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
        volatile int count;

        /**
         * Used as a reference so that we can recycle the Map entry
         * key for reuse when we are done with this value.
         *
         * NOTE: Only ever read and written from the same thread
         */
        EntryKey entryKey;

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

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || Entry.class != o.getClass()) return false;
            Entry entry = (Entry) o;
            return id.equals(entry.id) &&
                    lockType == entry.lockType &&
                    lockMode == entry.lockMode &&
                    owner.equals(entry.owner);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + lockType.hashCode();
            result = 31 * result + lockMode.hashCode();
            result = 31 * result + owner.hashCode();
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
}
