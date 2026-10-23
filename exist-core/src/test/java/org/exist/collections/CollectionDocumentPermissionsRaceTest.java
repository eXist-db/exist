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
package org.exist.collections;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that {@code fn:collection()} never returns a document that the user can't read, while another
 * thread changes the documents' owner and group, and that it returns each document that she can read while
 * another thread changes their access control lists.
 *
 * Each document has the mode {@code ---rwx---}, and moves back and forth between being owned by
 * alice:staff and by bob:other. alice is in staff but not other, and can read neither: in the first,
 * as its owner, only the owner bits apply to her; in the second, she is neither its owner nor in its
 * group. A change of owner and group is made in steps, though, and the intermediate bob:staff would
 * let her read it. Because each document's permissions are only read when no document is being
 * written, or under its READ_LOCK, she must never see that state.
 */
public class CollectionDocumentPermissionsRaceTest {

    private static final String COLLECTION = "/db/permissions-race";
    private static final String ACL_COLLECTION = "/db/acl-race";
    private static final int DOCUMENTS = 20;
    private static final int CHANGES = 300;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeAll
    public static void setUp() throws EXistException, PermissionDeniedException, XPathException, AuthenticationException {
        asAdmin("""
                (
                    sm:create-group("staff"),
                    sm:create-group("other"),
                    sm:create-account("alice", "alice", "staff", ()),
                    sm:create-account("bob", "bob", "other", ()),
                    sm:create-account("carol", "carol", "other", ()),
                    let $collection := xmldb:create-collection("/db", "permissions-race")
                    return (
                        sm:chmod(xs:anyURI($collection), "rwxr-xr-x"),
                        for $i in 1 to %d
                        let $doc := xs:anyURI(xmldb:store($collection, "doc" || $i || ".xml", <doc/>))
                        return (sm:chown($doc, "alice:staff"), sm:chmod($doc, "---rwx---"))
                    ),
                    let $collection := xmldb:create-collection("/db", "acl-race")
                    return (
                        sm:chmod(xs:anyURI($collection), "rwxr-xr-x"),
                        for $i in 1 to %d
                        let $doc := xs:anyURI(xmldb:store($collection, "doc" || $i || ".xml", <doc/>))
                        return (sm:chown($doc, "bob:other"), sm:chmod($doc, "r--r--r--"))
                    )
                )
                """.formatted(DOCUMENTS, DOCUMENTS));
    }

    @Test
    void neverReturnsADocumentInAnIntermediatePermissionState() throws Exception {
        race(i -> """
                for $doc in collection("%s") return sm:chown(document-uri($doc), "%s")
                """.formatted(COLLECTION, i % 2 == 0 ? "bob:other" : "alice:staff"), COLLECTION, 0);

        // both owner/group states deny alice
        assertEquals(0, asAlice("count(collection(\"" + COLLECTION + "\"))"));
        assertEquals(DOCUMENTS, asAdmin("count(collection(\"" + COLLECTION + "\"))").itemAt(0).toJavaObject(Integer.class));
    }

    /**
     * An access control entry for carol never applies to alice, who may read each document as "other",
     * but replacing the access control list while it is read may make the read fail, which must then
     * be repeated under the documents' locks.
     */
    @Test
    void returnsEachReadableDocumentWhileTheAccessControlListsChange() throws Exception {
        race(i -> i % 2 == 0
                ? """
                for $doc in collection("%s") return sm:add-user-ace(document-uri($doc), "carol", true(), "rwx")
                """.formatted(ACL_COLLECTION)
                : """
                for $doc in collection("%s") return sm:clear-acl(document-uri($doc))
                """.formatted(ACL_COLLECTION), ACL_COLLECTION, DOCUMENTS);

        // whatever the last change, carol's entry never applies to alice
        assertEquals(DOCUMENTS, asAlice("count(collection(\"" + ACL_COLLECTION + "\"))"));
    }

    /**
     * Makes {@link #CHANGES} changes to the documents, while alice counts the documents she can read.
     *
     * @param change the query which makes the nth change.
     * @param collection the collection which alice reads.
     * @param readable the number of documents which alice should read each time.
     */
    private static void race(final IntFunction<String> change, final String collection, final int readable) throws Exception {
        final LockManager lockManager = existEmbeddedServer.getBrokerPool().getLockManager();
        final long failedBefore = lockManager.getOptimisticDocumentReadsFailed();

        final AtomicBoolean changing = new AtomicBoolean(true);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> writer = executor.submit(() -> {
                try {
                    for (int i = 0; i < CHANGES; i++) {
                        asAdmin(change.apply(i));
                    }
                } finally {
                    changing.set(false);
                }
                return null;
            });

            final Future<Integer> reader = executor.submit(() -> {
                int reads = 0;
                while (changing.get()) {
                    assertEquals(readable, asAlice("count(collection(\"" + collection + "\"))"), "alice read the wrong number of documents");
                    reads++;
                }
                return reads;
            });

            writer.get(5, TimeUnit.MINUTES);
            assertTrue(reader.get(1, TimeUnit.MINUTES) > 0);
        } finally {
            executor.shutdownNow();
        }

        // the documents were read with their locks at least once, after an optimistic read was not possible or invalidated
        assertTrue(lockManager.getOptimisticDocumentReadsFailed() > failedBefore);
    }

    private static Sequence asAdmin(final String query) throws EXistException, PermissionDeniedException, XPathException, AuthenticationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        return execute(pool, admin, query);
    }

    private static int asAlice(final String query) throws EXistException, PermissionDeniedException, XPathException, AuthenticationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject alice = pool.getSecurityManager().authenticate("alice", "alice");
        return execute(pool, alice, query).itemAt(0).toJavaObject(Integer.class);
    }

    private static Sequence execute(final BrokerPool pool, final Subject subject, final String query) throws EXistException, PermissionDeniedException, XPathException {
        try (final DBBroker broker = pool.get(Optional.of(subject))) {
            final XQuery xquery = pool.getXQueryService();
            return xquery.execute(broker, query, null);
        }
    }
}
