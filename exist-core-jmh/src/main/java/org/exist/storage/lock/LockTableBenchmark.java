/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
package org.exist.storage.lock;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class LockTableBenchmark {

    private static final int DATA_SUB_COLLECTIONS = 13;
    private static final int DOCUMENTS = 20;

    private static final int EVENTS_BTREE_READ_LOCK = 593062;
    private static final int EVENTS_COLLECTION_INTENTION_READ_LOCK = 86619;
    private static final int EVENTS_COLLECTION_READ_LOCK = 19630;
    private static final int EVENTS_DOCUMENT_READ_LOCK = 60370;


    @State(Scope.Benchmark)
    public static class LockTableState {
        private final LockTable lockTable = new LockTable();
    }

    @State(Scope.Thread)
    public static class EventsState {
        private int btreeReads = 0;
        private int collectionIntentionReads = 0;
        private int collectionReads = 0;
        private int documentReads = 0;

        private int dataSubCollectionIndex = 0;
        private int documentsIndex = 0;
    }

    @Benchmark
    public void testEvent(final LockTableState lockTableState, final EventsState eventsState) {
        while (!(eventsState.collectionIntentionReads >= EVENTS_COLLECTION_INTENTION_READ_LOCK
                && eventsState.collectionReads >= EVENTS_COLLECTION_READ_LOCK
                && eventsState.documentReads >= EVENTS_DOCUMENT_READ_LOCK
                && eventsState.btreeReads >= EVENTS_BTREE_READ_LOCK
        )) {

            final long groupId = System.nanoTime();

            if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                eventsState.btreeReads++;
                lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
            }

            boolean didCollectionIntentionRead = false;
            if (eventsState.collectionIntentionReads < EVENTS_COLLECTION_INTENTION_READ_LOCK) {
                lockTableState.lockTable.attempt(groupId, "/db", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.acquired(groupId, "/db", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                eventsState.collectionIntentionReads++;

                if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                    lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    eventsState.btreeReads++;
                    lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                }

                lockTableState.lockTable.attempt(groupId, "/db/apps", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.acquired(groupId, "/db/apps", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.released(groupId, "/db", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                eventsState.collectionIntentionReads++;

                if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                    lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    eventsState.btreeReads++;
                    lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                }

                lockTableState.lockTable.attempt(groupId, "/db/apps/docs", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.acquired(groupId, "/db/apps/docs", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.released(groupId, "/db/apps", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                eventsState.collectionIntentionReads++;

                if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                    lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    eventsState.btreeReads++;
                    lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                }

                lockTableState.lockTable.attempt(groupId, "/db/apps/docs/data", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.acquired(groupId, "/db/apps/docs/data", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                lockTableState.lockTable.released(groupId, "/db/apps/docs", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
                eventsState.collectionIntentionReads++;

                if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                    lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    eventsState.btreeReads++;
                    lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                }

                didCollectionIntentionRead = true;
            }

            if (eventsState.dataSubCollectionIndex > DATA_SUB_COLLECTIONS) {
                eventsState.dataSubCollectionIndex = 0;
            }
            final String dataSubCollection = "/db/apps/docs/data/" + eventsState.dataSubCollectionIndex++;

            boolean didCollectionRead = false;
            if (eventsState.collectionReads < EVENTS_COLLECTION_READ_LOCK) {

                if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                    lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    eventsState.btreeReads++;
                    lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                }

                lockTableState.lockTable.attempt(groupId, dataSubCollection, Lock.LockType.COLLECTION, Lock.LockMode.READ_LOCK);
                lockTableState.lockTable.acquired(groupId, dataSubCollection, Lock.LockType.COLLECTION, Lock.LockMode.READ_LOCK);
                eventsState.collectionReads++;

                didCollectionRead = true;
            }

            if (didCollectionIntentionRead) {
                lockTableState.lockTable.released(groupId, "/db/apps/docs/data", Lock.LockType.COLLECTION, Lock.LockMode.INTENTION_READ);
            }

            if (eventsState.documentReads < EVENTS_DOCUMENT_READ_LOCK) {
                if (eventsState.btreeReads < EVENTS_BTREE_READ_LOCK) {
                    lockTableState.lockTable.attempt(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    lockTableState.lockTable.acquired(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                    eventsState.btreeReads++;
                    lockTableState.lockTable.released(groupId, "BTREE", Lock.LockType.BTREE, Lock.LockMode.READ_LOCK);
                }

                if (eventsState.documentsIndex > DOCUMENTS) {
                    eventsState.documentsIndex = 0;
                }
                final String document = dataSubCollection + '/' + eventsState.documentsIndex++;
                lockTableState.lockTable.attempt(groupId, document, Lock.LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
                lockTableState.lockTable.acquired(groupId, document, Lock.LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
                eventsState.documentReads++;

                lockTableState.lockTable.released(groupId, document, Lock.LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
            }

            if (didCollectionRead) {
                lockTableState.lockTable.released(groupId, dataSubCollection, Lock.LockType.COLLECTION, Lock.LockMode.READ_LOCK);
            }
        }
    }

    public static void main(final String args[]) {
        // NOTE: just for running with the java debugger
        LockTableBenchmark lockTableBenchmark = new LockTableBenchmark();
        LockTableState lockTableState = new LockTableState();
        EventsState eventsState = new EventsState();

        lockTableBenchmark.testEvent(lockTableState, eventsState);

        lockTableState.lockTable.shutdown();
    }
}
