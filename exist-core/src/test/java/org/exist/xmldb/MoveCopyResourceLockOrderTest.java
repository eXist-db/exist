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
package org.exist.xmldb;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.lock.LockTable;
import org.exist.storage.lock.LockTable.LockEventListener;
import org.exist.storage.lock.LockTable.LockEventType;
import org.exist.test.ExistWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Checks the order in which moving and copying a document take their locks, since a wrong order can deadlock
 * with concurrent readers or writers:
 *
 * <ul>
 *     <li>no Collection lock may be requested while holding a document lock, as a reader locks Collections
 *     before their documents (see https://github.com/eXist-db/exist/issues/6774);</li>
 *     <li>no Collection WRITE_LOCK may be requested while holding only a READ_LOCK or INTENTION_READ lock on
 *     the same Collection, as two threads doing so wait for each other.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class MoveCopyResourceLockOrderTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    private static final String TEST_COLLECTION_NAME = "testMoveCopyLockOrder";
    private static final String TEST_COLLECTION_PATH = "/db/" + TEST_COLLECTION_NAME;
    private static final String SOURCE_COLLECTION_NAME = "source";
    private static final String TARGET_COLLECTION_NAME = "target";
    private static final String DOCUMENT_NAME = "doc.xml";

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public String baseUri;

    private Collection sourceCollection;

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "local", "xmldb:exist://" },
                { "remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc" }
        });
    }

    private String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test
    public void moveResource() throws XMLDBException, InterruptedException, EXistException {
        final EXistCollectionManagementService service = sourceCollection.getService(EXistCollectionManagementService.class);
        final List<String> violations = recordLockOrderViolations(() ->
                service.moveResource(XmldbURI.create(DOCUMENT_NAME), XmldbURI.create(TEST_COLLECTION_PATH + "/" + TARGET_COLLECTION_NAME), null));
        assertEquals(List.of(), violations);
    }

    @Test
    public void copyResource() throws XMLDBException, InterruptedException, EXistException {
        final EXistCollectionManagementService service = sourceCollection.getService(EXistCollectionManagementService.class);
        final List<String> violations = recordLockOrderViolations(() ->
                service.copyResource(XmldbURI.create(DOCUMENT_NAME), XmldbURI.create(TEST_COLLECTION_PATH + "/" + TARGET_COLLECTION_NAME), null));
        assertEquals(List.of(), violations);
    }

    private List<String> recordLockOrderViolations(final XmldbOperation operation) throws XMLDBException, InterruptedException, EXistException {
        final LockTable lockTable = BrokerPool.getInstance().getLockManager().getLockTable();
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
        return listener.violations();
    }

    @FunctionalInterface
    private interface XmldbOperation {
        void run() throws XMLDBException;
    }

    private record LockEvent(LockEventType eventType, String id, LockType lockType, LockMode lockMode, String owner, int count) {
    }

    private static class RecordingListener implements LockEventListener {
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
         * Replays the recorded events of each thread that locked something in the test Collection.
         */
        List<String> violations() {
            final List<LockEvent> recorded;
            synchronized (events) {
                recorded = List.copyOf(events);
            }
            final Set<String> owners = recorded.stream()
                    .filter(event -> event.id().startsWith(TEST_COLLECTION_PATH))
                    .map(LockEvent::owner)
                    .collect(Collectors.toSet());

            final List<String> violations = new ArrayList<>();
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
                final List<String> violations) {
            if (event.lockType() != LockType.COLLECTION) {
                return;
            }
            final Map<LockMode, Integer> modes = ownerHeld.getOrDefault(LockType.COLLECTION, Map.of()).getOrDefault(event.id(), Map.of());
            final List<String> documents = ownerHeld.getOrDefault(LockType.DOCUMENT, Map.of()).entrySet().stream()
                    .filter(document -> !document.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .toList();
            if (!documents.isEmpty() && modes.keySet().stream().noneMatch(heldMode -> covers(heldMode, event.lockMode()))) {
                violations.add(event.owner() + " requested " + event.lockMode() + " on Collection " + event.id() + " while holding document " + documents);
            }
            if (event.lockMode() == LockMode.WRITE_LOCK && !modes.containsKey(LockMode.WRITE_LOCK)
                    && (modes.containsKey(LockMode.READ_LOCK) || modes.containsKey(LockMode.INTENTION_READ))) {
                violations.add(event.owner() + " requested WRITE_LOCK on Collection " + event.id() + " while holding " + modes.keySet());
            }
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

    @Before
    public void setUp() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService rootService = root.getService(CollectionManagementService.class);
        final Collection testCollection = rootService.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);

        final CollectionManagementService service = testCollection.getService(CollectionManagementService.class);
        sourceCollection = service.createCollection(SOURCE_COLLECTION_NAME);
        assertNotNull(service.createCollection(TARGET_COLLECTION_NAME));

        final XMLResource document = sourceCollection.createResource(DOCUMENT_NAME, XMLResource.class);
        document.setContent("<doc/>");
        sourceCollection.storeResource(document);
    }

    @After
    public void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        sourceCollection = null;
    }
}
