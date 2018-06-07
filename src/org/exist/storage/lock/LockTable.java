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

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.txn.Txn;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.exist.storage.lock.LockTable.LockAction.Action.*;

/**
 * The Lock Table holds the details of
 * threads awaiting to acquire a Lock
 * and threads that have acquired a lock
 *
 * It is arranged by the id of the lock
 * which is typically an indicator of the
 * lock subject
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTable {

    public final static String PROP_DISABLE = "exist.locktable.disable";
    public final static String PROP_SANITY_CHECK = "exist.locktable.sanity.check";
    public final static String PROP_TRACE_STACK_DEPTH = "exist.locktable.trace.stack.depth";

    private final static Logger LOG = LogManager.getLogger(LockTable.class);
    private final static LockTable instance = new LockTable();

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
     * List of threads attempting to acquire a lock
     *
     * Map<Id, Map<Lock Type, List<LockModeOwner>>>
     */
    private final ConcurrentMap<String, Map<LockType, List<LockModeOwner>>> attempting = new ConcurrentHashMap<>();

    /**
     * Reference count of acquired locks by id and type
     *
     * Map<Id, Map<Lock Type, Map<Lock Mode, Map<Owner, HoldCount>>>>
     */
    private final ConcurrentMap<String, Map<LockType, Map<LockMode, Map<String, Integer>>>> acquired = new ConcurrentHashMap<>();

    /**
     * The {@link #queue} holds lock events and lock listener events
     * and is processed by the single thread {@link #queueConsumer} which uses
     * {@link QueueConsumer} to ensure serializability of locking events and monitoring
     */
    private final TransferQueue<Either<ListenerAction, LockAction>> queue = new LinkedTransferQueue<>();
    private final Future<?> queueConsumer;

    private LockTable() {
        final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "exist-lockTable.processor"));
        this.queueConsumer = executorService.submit(new QueueConsumer(queue, attempting, acquired));

        // add a log listener if trace level logging is enabled
        if(LOG.isTraceEnabled()) {
            registerListener(new LockEventLogListener(LOG, Level.TRACE));
        }
    }

    public static LockTable getInstance() {
        return instance;
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

    @Deprecated
    public void released(final long groupId, final String id, final LockType lockType, final LockMode mode, final int count) {
        event(Released, groupId, id, lockType, mode, count);
    }

    private void event(final LockAction.Action action, final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(action, groupId, id, lockType, mode, 1);
    }

    private void event(final LockAction.Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final int count) {
        if(disableEvents) {
            return;
        }

        final long timestamp = System.nanoTime();
        final Thread currentThread = Thread.currentThread();
        final String threadName = currentThread.getName();
        @Nullable final StackTraceElement[] stackTrace = getStackTrace(currentThread);

        if(ignoreEvent(threadName, id)) {
            return;
        }

        final LockAction lockAction = new LockAction(action, groupId, id, lockType, mode, threadName, count, timestamp, stackTrace);

        /**
         * Very useful for debugging Lock life cycles
         */
        if(sanityCheck) {
            sanityCheckLockLifecycles(lockAction);
        }

        queue.add(Either.Right(lockAction));
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
        if(traceStackDepth == 0) {
            return null;
        } else {
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
    }

    private static final String THIS_CLASS_NAME = LockTable.class.getName();

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
        final ListenerAction listenerAction = new ListenerAction(ListenerAction.Action.Register, lockEventListener);
        queue.add(Either.Left(listenerAction));
    }

    public void deregisterListener(final LockEventListener lockEventListener) {
        final ListenerAction listenerAction = new ListenerAction(ListenerAction.Action.Deregister, lockEventListener);
        queue.add(Either.Left(listenerAction));
    }

    public boolean hasPendingEvents() {
        return !queue.isEmpty();
    }

    /**
     * Get's a copy of the current lock attempt information
     *
     * @return lock attempt information
     */
    public Map<String, Map<LockType, List<LockModeOwner>>> getAttempting() {
        return new HashMap<>(attempting);
    }

    /**
     * Get's a copy of the current acquired lock information
     *
     * @return acquired lock information
     */
    public Map<String, Map<LockType, Map<LockMode, Map<String, Integer>>>> getAcquired() {
        return new HashMap<>(acquired);
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

    private static class QueueConsumer implements Runnable {
        private final TransferQueue<Either<ListenerAction, LockAction>> queue;
        private final ConcurrentMap<String, Map<LockType, List<LockModeOwner>>> attempting;
        private final ConcurrentMap<String, Map<LockType, Map<LockMode, Map<String, Integer>>>> acquired;
        private final List<LockEventListener> listeners = new ArrayList<>();

        QueueConsumer(final TransferQueue<Either<ListenerAction, LockAction>> queue,
                      final ConcurrentMap<String, Map<LockType, List<LockModeOwner>>> attempting,
                      final ConcurrentMap<String, Map<LockType, Map<LockMode, Map<String, Integer>>>> acquired) {
            this.queue = queue;
            this.attempting = attempting;
            this.acquired = acquired;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final Either<ListenerAction, LockAction> event = queue.take();

                    if(event.isLeft()) {
                        processListenerAction(event.left().get());
                    } else {
                        processLockAction(event.right().get());
                    }

                } catch (final InterruptedException e) {
                    LOG.fatal("LockTable.QueueConsumer was interrupted");
                }
            }
        }

        private void processListenerAction(final ListenerAction listenerAction) {
            if(listenerAction.action == ListenerAction.Action.Register) {
                listeners.add(listenerAction.lockEventListener);
                listenerAction.lockEventListener.registered();
            } else if(listenerAction.action == ListenerAction.Action.Deregister) {
                listeners.remove(listenerAction.lockEventListener);
                listenerAction.lockEventListener.unregistered();
            }
        }

        private void processLockAction(final LockAction lockAction) {
            if (lockAction.action == Attempt) {
                notifyListenersOfAttempt(lockAction);
                addToAttempting(lockAction);

            } else if (lockAction.action == AttemptFailed) {
                removeFromAttempting(lockAction);
                notifyListenersOfAttemptFailed(lockAction);

            } else if (lockAction.action == Acquired) {
                removeFromAttempting(lockAction);
                incrementAcquired(lockAction);

            } else if (lockAction.action == Released) {
                decrementAcquired(lockAction);
            }
        }

        private void notifyListenersOfAttempt(final LockAction lockAction) {
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(lockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void notifyListenersOfAttemptFailed(final LockAction lockAction) {
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(lockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void notifyListenersOfAcquire(final LockAction lockAction, final int newReferenceCount) {
            final LockAction newLockAction = lockAction.withCount(newReferenceCount);
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(newLockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void notifyListenersOfRelease(final LockAction lockAction, final int newReferenceCount) {
            final LockAction newLockAction = lockAction.withCount(newReferenceCount);
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(newLockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void addToAttempting(final LockAction lockAction) {
            attempting.compute(lockAction.id, (id, attempts) -> {
                if (attempts == null) {
                    attempts = new HashMap<>();
                }

                attempts.compute(lockAction.lockType, (lockType, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }

                    v.add(new LockModeOwner(lockAction.mode, lockAction.threadName));
                    return v;
                });

                return attempts;
            });
        }

        private void removeFromAttempting(final LockAction lockAction) {
            attempting.compute(lockAction.id, (id, attempts) -> {
                if (attempts == null) {
                    return null;
                } else {
                    attempts.compute(lockAction.lockType, (lockType, v) -> {
                        if (v == null) {
                            return null;
                        }

                        v.removeIf(val -> val.getLockMode() == lockAction.mode && val.getOwnerThread().equals(lockAction.threadName));
                        if (v.isEmpty()) {
                            return null;
                        } else {
                            return v;
                        }
                    });

                    if (attempts.isEmpty()) {
                        return null;
                    } else {
                        return attempts;
                    }
                }
            });
        }

        private void incrementAcquired(final LockAction lockAction) {
            acquired.compute(lockAction.id, (id, acqu) -> {
                if (acqu == null) {
                    acqu = new HashMap<>();
                }

                acqu.compute(lockAction.lockType, (lockType, v) -> {
                    if (v == null) {
                        v = new HashMap<>();
                    }

                    v.compute(lockAction.mode, (mode, ownerHolds) -> {
                        if (ownerHolds == null) {
                            ownerHolds = new HashMap<>();
                        }

                        ownerHolds.compute(lockAction.threadName, (threadName, holdCount) -> {
                            if(holdCount == null) {
                                holdCount = 0;
                            }
                            return ++holdCount;
                        });

                        final int lockModeHolds = ownerHolds.values().stream().collect(Collectors.summingInt(Integer::intValue));
                        notifyListenersOfAcquire(lockAction, lockModeHolds);

                        return ownerHolds;
                    });

                    return v;
                });

                return acqu;
            });
        }

        private void decrementAcquired(final LockAction lockAction) {
            acquired.compute(lockAction.id, (id, acqu) -> {
                if (acqu == null) {
                    LOG.error("No entry found when trying to decrementAcquired for: id={}" + lockAction.id);
                    return null;
                }

                acqu.compute(lockAction.lockType, (lockType, v) -> {
                    if (v == null) {
                        LOG.error("No entry found when trying to decrementAcquired for: id={}, lockType={}", lockAction.id, lockAction.lockType);
                        return null;
                    }

                    v.compute(lockAction.mode, (mode, ownerHolds) -> {
                        if (ownerHolds == null) {
                            LOG.error("No entry found when trying to decrementAcquired for: id={}, lockType={}, lockMode={}", lockAction.id, lockAction.lockType, lockAction.mode);
                            return null;
                        } else {
                            ownerHolds.compute(lockAction.threadName, (threadName, holdCount) -> {
                                if(holdCount == null) {
                                    LOG.error("No entry found when trying to decrementAcquired for: id={}, lockType={}, lockMode={}, threadName={}", lockAction.id, lockAction.lockType, lockAction.mode, lockAction.threadName);
                                    return null;
                                } else if(holdCount == 0) {
                                    LOG.error("Negative release when trying to decrementAcquired for: id={}, lockType={}, lockMode={}, threadName={}", lockAction.id, lockAction.lockType, lockAction.mode, lockAction.threadName);
                                    return null;
                                } else if(holdCount == 1) {
                                    return null;
                                } else {
                                    return --holdCount;
                                }
                            });

                            final int lockModeHolds = ownerHolds.values().stream().collect(Collectors.summingInt(Integer::intValue));

                            notifyListenersOfRelease(lockAction, lockModeHolds);

                            if (ownerHolds.isEmpty()) {
                                return null;
                            } else {
                                return ownerHolds;
                            }
                        }
                    });

                    if (v.isEmpty()) {
                        return null;
                    } else {
                        return v;
                    }
                });

                if (acqu.isEmpty()) {
                    return null;
                } else {
                    return acqu;
                }
            });
        }
    }

    public interface LockEventListener {
        default void registered() {}
        void accept(final LockAction lockAction);
        default void unregistered() {}
    }

    private static class ListenerAction {
        enum Action {
            Register,
            Deregister
        }

        private final Action action;
        private final LockEventListener lockEventListener;

        public ListenerAction(final Action action, final LockEventListener lockEventListener) {
            this.action = action;
            this.lockEventListener = lockEventListener;
        }

        @Override
        public String toString() {
            return action.name() + " " + lockEventListener.getClass().getName();
        }
    }

    public static class LockAction {
        public enum Action {
            Attempt,
            AttemptFailed,
            Acquired,
            Released
        }

        public final Action action;
        public final long groupId;
        public final String id;
        public final LockType lockType;
        public final LockMode mode;
        public final String threadName;
        public final int count;
        /**
         * System#nanoTime()
         */
        public final long timestamp;
        @Nullable public final StackTraceElement[] stackTrace;

        LockAction(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final String threadName, final int count, final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
            this.action = action;
            this.groupId = groupId;
            this.id = id;
            this.lockType = lockType;
            this.mode = mode;
            this.threadName = threadName;
            this.count = count;
            this.timestamp = timestamp;
            this.stackTrace = stackTrace;
        }

        public LockAction withCount(final int count) {
            return new LockAction(action, groupId, id, lockType, mode, threadName, count, timestamp, stackTrace);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder()
                    .append(action.toString())
                    .append(' ')
                    .append(lockType.name());

                if(groupId > -1) {
                    builder
                            .append("#")
                            .append(groupId);
                }

                builder.append('(')
                    .append(mode.toString())
                    .append(") of ")
                    .append(id);

            if(stackTrace != null) {
                final String reason = getSimpleStackReason();
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

            if (action == Acquired || action == Released) {
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
        public String getSimpleStackReason() {
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
    }

    /** debugging tools below **/


    /**
     * Holds a count of READ and WRITE locks by {@link LockAction#id}
     */
    private final Map<String, Tuple2<Long, Long>> lockCounts = new HashMap<>();

    /**
     * Checks that there are not more releases that there are acquires
     */
    private void sanityCheckLockLifecycles(final LockAction lockAction) {
        synchronized(lockCounts) {
            long read = 0;
            long write = 0;

            final Tuple2<Long, Long> lockCount = lockCounts.get(lockAction.id);
            if(lockCount != null) {
                read = lockCount._1;
                write = lockCount._2;
            }

            if(lockAction.action == LockAction.Action.Acquired) {
                if(lockAction.mode == LockMode.READ_LOCK) {
                    read++;
                } else if(lockAction.mode == LockMode.WRITE_LOCK) {
                    write++;
                }
            } else if(lockAction.action == LockAction.Action.Released) {
                if(lockAction.mode == LockMode.READ_LOCK) {
                    if(read == 0) {
                        LOG.error("Negative READ_LOCKs", new IllegalStateException());
                    }
                    read--;
                } else if(lockAction.mode == LockMode.WRITE_LOCK) {
                    if(write == 0) {
                        LOG.error("Negative WRITE_LOCKs", new IllegalStateException());
                    }
                    write--;
                }
            }

            if(LOG.isTraceEnabled()) {
                LOG.trace("QUEUE: {} (read={} write={})", lockAction.toString(), read, write);
            }

            lockCounts.put(lockAction.id, new Tuple2<>(read, write));
        }
    }

}
