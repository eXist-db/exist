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

import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lock table reads the clock, for event timestamps and group ids, only when a listener is registered to
 * report them to. Check that listeners still get both.
 */
class LockTableEventsTest {

    private record Event(LockTable.LockEventType type, long timestamp, long groupId, String id) {}

    private static final class RecordingListener implements LockTable.LockEventListener {
        final List<Event> events = new ArrayList<>();

        @Override
        public synchronized void accept(final LockTable.LockEventType lockEventType, final long timestamp,
                final long groupId, final LockTable.Entry entry) {
            events.add(new Event(lockEventType, timestamp, groupId, entry.getId()));
        }
    }

    @Test
    void noGroupIdWithoutListener() {
        final LockTable lockTable = new LockManager(4).getLockTable();
        assertEquals(LockTable.NO_GROUP, lockTable.newGroupId());
    }

    @Test
    void listenerGetsTimestampsAndOneGroupIdPerLock() throws LockException {
        final LockManager lockManager = new LockManager(4);
        final LockTable lockTable = lockManager.getLockTable();
        final RecordingListener listener = new RecordingListener();
        lockTable.registerListener(listener);
        try {
            final long before = System.nanoTime();
            try (final ManagedDocumentLock lock = lockManager.acquireDocumentReadLock(XmldbURI.create("/db/a/doc1.xml"))) {
                // held
            }
            try (final ManagedDocumentLock lock = lockManager.acquireDocumentWriteLock(XmldbURI.create("/db/a/doc2.xml"))) {
                // held
            }
            final long after = System.nanoTime();

            final List<Event> events;
            synchronized (listener) {
                events = new ArrayList<>(listener.events);
            }
            assertEquals(List.of(LockTable.LockEventType.Attempt, LockTable.LockEventType.Acquired, LockTable.LockEventType.Released,
                    LockTable.LockEventType.Attempt, LockTable.LockEventType.Acquired, LockTable.LockEventType.Released),
                    events.stream().map(Event::type).toList());

            for (final Event event : events) {
                assertTrue(event.timestamp() >= before && event.timestamp() <= after, "timestamp of " + event);
                assertTrue(event.groupId() > LockTable.NO_GROUP, "group id of " + event);
            }
            for (int i = 1; i < events.size(); i++) {
                assertTrue(events.get(i).timestamp() >= events.get(i - 1).timestamp(), "timestamps in order");
            }

            // the three events of a lock share its group id, and the two locks have different ones
            assertEquals(events.get(0).groupId(), events.get(1).groupId());
            assertEquals(events.get(0).groupId(), events.get(2).groupId());
            assertEquals(events.get(3).groupId(), events.get(4).groupId());
            assertEquals(events.get(3).groupId(), events.get(5).groupId());
            assertTrue(events.get(0).groupId() != events.get(3).groupId(), "a new group id per lock");
        } finally {
            lockTable.deregisterListener(listener);
        }
        assertEquals(LockTable.NO_GROUP, lockTable.newGroupId());
    }
}
