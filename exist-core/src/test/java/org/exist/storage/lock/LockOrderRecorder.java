/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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

import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.lock.LockTable.LockEventListener;
import org.exist.storage.lock.LockTable.LockEventType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Records the lock events of an operation and reports the lock requests that can deadlock with concurrent
 * readers or writers:
 *
 * <ul>
 *     <li>a Collection lock requested while holding a document lock, as a reader locks Collections before their
 *     documents (see https://github.com/eXist-db/exist/issues/6774);</li>
 *     <li>a Collection lock requested in a mode that the locks the thread already holds on that Collection don't
 *     cover, for example a WRITE_LOCK while holding a READ_LOCK or INTENTION_READ: the request has to wait for the
 *     other holders, which may themselves wait for this thread.</li>
 * </ul>
 *
 * A request on a Collection below one the thread holds a WRITE_LOCK on is never reported, as that lock covers the
 * whole sub-tree.
 *
 * Only the threads that locked something under the given path are checked.
 */
public final class LockOrderRecorder {

    @FunctionalInterface
    public interface Operation {
        void run() throws Exception;
    }

    public enum Kind {
        /** A Collection lock requested while holding a document lock. */
        COLLECTION_AFTER_DOCUMENT,
        /** A Collection lock requested in a mode the thread's own locks on that Collection don't cover. */
        UPGRADE
    }

    public record Violation(Kind kind, String description) {
        @Override
        public String toString() {
            return kind + ": " + description;
        }
    }

    private LockOrderRecorder() {
    }

    /**
     * Runs the operation and returns the lock requests it made that can deadlock.
     *
     * @param pool the database whose locks to record
     * @param pathPrefix only threads that locked a Collection or document whose path starts with this are checked
     * @param operation the operation to run
     * @return each such request, empty if there are none
     * @throws Exception if the operation throws
     */
    public static List<Violation> violations(final BrokerPool pool, final String pathPrefix, final Operation operation) throws Exception {
        final LockTable lockTable = pool.getLockManager().getLockTable();
        final RecordingListener listener = new RecordingListener();
        lockTable.registerListener(listener);
        try {
            while (!listener.registered.get()) {
                Thread.sleep(1);
            }
            operation.run();
        } finally {
            lockTable.deregisterListener(listener);
        }
        while (listener.registered.get()) {
            Thread.sleep(1);
        }
        return listener.violations(pathPrefix);
    }

    private record LockEvent(LockEventType eventType, String id, LockType lockType, LockMode lockMode, String owner, int count) {
    }

    private static final class RecordingListener implements LockEventListener {
        private final AtomicBoolean registered = new AtomicBoolean();
        private final List<LockEvent> events = new ArrayList<>();

        @Override
        public void registered() {
            registered.set(true);
        }

        @Override
        public void unregistered() {
            registered.set(false);
        }

        @Override
        public void accept(final LockEventType lockEventType, final long timestamp, final long groupId, final LockTable.Entry entry) {
            // read count first to ensure memory visibility of the entry's other fields
            final int count = entry.getCount();
            final LockEvent event = new LockEvent(lockEventType, entry.getId(), entry.getLockType(), entry.getLockMode(), entry.getOwner(), count);
            synchronized (events) {
                events.add(event);
            }
        }

        /**
         * Replays the recorded events of each thread that locked something under the path prefix.
         */
        List<Violation> violations(final String pathPrefix) {
            final List<LockEvent> recorded;
            synchronized (events) {
                recorded = List.copyOf(events);
            }
            final Set<String> owners = recorded.stream()
                    .filter(event -> event.id().startsWith(pathPrefix))
                    .map(LockEvent::owner)
                    .collect(Collectors.toSet());

            final List<Violation> violations = new ArrayList<>();
            final Map<String, Map<LockType, Map<String, Map<LockMode, Integer>>>> held = new HashMap<>();
            for (final LockEvent event : recorded) {
                if (!owners.contains(event.owner())) {
                    continue;
                }
                final Map<LockType, Map<String, Map<LockMode, Integer>>> ownerHeld = held.computeIfAbsent(event.owner(), k -> new EnumMap<>(LockType.class));
                switch (event.eventType()) {
                    case Attempt -> checkAttempt(event, ownerHeld, violations);
                    case Acquired -> ownerHeld.computeIfAbsent(event.lockType(), k -> new HashMap<>())
                            .computeIfAbsent(event.id(), k -> new EnumMap<>(LockMode.class))
                            .merge(event.lockMode(), 1, Integer::sum);
                    case Released -> {
                        final Map<LockMode, Integer> modes = ownerHeld.getOrDefault(event.lockType(), Map.of()).get(event.id());
                        if (modes != null) {
                            // a lock taken before recording started has no count to decrement
                            modes.computeIfPresent(event.lockMode(), (mode, count) -> count > 1 ? count - 1 : null);
                        }
                    }
                    default -> { }
                }
            }
            return violations;
        }

        private static void checkAttempt(final LockEvent event, final Map<LockType, Map<String, Map<LockMode, Integer>>> ownerHeld,
                final List<Violation> violations) {
            if (event.lockType() != LockType.COLLECTION) {
                return;
            }
            final Map<String, Map<LockMode, Integer>> collections = ownerHeld.getOrDefault(LockType.COLLECTION, Map.of());
            final Map<LockMode, Integer> modes = collections.getOrDefault(event.id(), Map.of());
            final boolean covered = modes.keySet().stream().anyMatch(heldMode -> covers(heldMode, event.lockMode()))
                    || writeLocksAncestor(collections, event.id());
            final List<String> documents = ownerHeld.getOrDefault(LockType.DOCUMENT, Map.of()).entrySet().stream()
                    .filter(document -> !document.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .toList();
            if (!documents.isEmpty() && !covered) {
                violations.add(new Violation(Kind.COLLECTION_AFTER_DOCUMENT,
                        event.owner() + " requested " + event.lockMode() + " on Collection " + event.id() + " while holding document " + documents));
            }
            if (!modes.isEmpty() && !covered) {
                violations.add(new Violation(Kind.UPGRADE,
                        event.owner() + " requested " + event.lockMode() + " on Collection " + event.id() + " while holding " + modes.keySet()));
            }
        }

        /**
         * Whether the thread holds a WRITE_LOCK on an ancestor of the Collection: that lock covers the whole
         * sub-tree, so no other thread can hold or wait for a lock below it.
         */
        private static boolean writeLocksAncestor(final Map<String, Map<LockMode, Integer>> collections, final String id) {
            return collections.entrySet().stream()
                    .anyMatch(held -> id.startsWith(held.getKey() + "/") && held.getValue().containsKey(LockMode.WRITE_LOCK));
        }

        /**
         * Whether a thread holding a lock in {@code heldMode} is granted {@code requestedMode} on the same lock
         * without waiting, whatever other threads hold or wait for.
         */
        private static boolean covers(final LockMode heldMode, final LockMode requestedMode) {
            return switch (heldMode) {
                case WRITE_LOCK -> true;
                case READ_LOCK -> requestedMode == LockMode.READ_LOCK || requestedMode == LockMode.INTENTION_READ;
                case INTENTION_WRITE -> requestedMode == LockMode.INTENTION_WRITE || requestedMode == LockMode.INTENTION_READ;
                case INTENTION_READ -> requestedMode == LockMode.INTENTION_READ;
                default -> false;
            };
        }
    }
}
