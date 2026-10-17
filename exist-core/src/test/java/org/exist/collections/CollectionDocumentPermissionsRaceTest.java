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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that {@code fn:collection()} never returns a document that the user can't read, while another
 * thread changes the documents' owner and group.
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
                    let $collection := xmldb:create-collection("/db", "permissions-race")
                    return (
                        sm:chmod(xs:anyURI($collection), "rwxr-xr-x"),
                        for $i in 1 to %d
                        let $doc := xs:anyURI(xmldb:store($collection, "doc" || $i || ".xml", <doc/>))
                        return (sm:chown($doc, "alice:staff"), sm:chmod($doc, "---rwx---"))
                    )
                )
                """.formatted(DOCUMENTS));
    }

    @Test
    void neverReturnsADocumentInAnIntermediatePermissionState() throws Exception {
        final AtomicBoolean changing = new AtomicBoolean(true);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> writer = executor.submit(() -> {
                try {
                    for (int i = 0; i < CHANGES; i++) {
                        final String ownerGroup = i % 2 == 0 ? "bob:other" : "alice:staff";
                        asAdmin("""
                                for $doc in collection("%s") return sm:chown(document-uri($doc), "%s")
                                """.formatted(COLLECTION, ownerGroup));
                    }
                } finally {
                    changing.set(false);
                }
                return null;
            });

            final Future<Integer> reader = executor.submit(() -> {
                int reads = 0;
                while (changing.get()) {
                    assertEquals(0, asAlice("count(collection(\"" + COLLECTION + "\"))"), "alice could read a document");
                    reads++;
                }
                return reads;
            });

            writer.get(5, TimeUnit.MINUTES);
            assertTrue(reader.get(1, TimeUnit.MINUTES) > 0);
        } finally {
            executor.shutdownNow();
        }

        // both owner/group states deny alice
        assertEquals(0, asAlice("count(collection(\"" + COLLECTION + "\"))"));
        assertEquals(DOCUMENTS, asAdmin("count(collection(\"" + COLLECTION + "\"))").itemAt(0).toJavaObject(Integer.class));
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
