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
package org.exist.indexing.lucene;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.exist.storage.BrokerPool;
import org.exist.storage.UpdateListener;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;
import org.exist.xmldb.IndexQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An optimised {@code //p[ft:query(., $term)]} asks the Lucene index for the matching nodes and hands that
 * narrowed set to the path's location step as preloaded data. A location step's update listener clears its
 * node set on every document-added or document-removed notification, and preloaded data is held in the same
 * field, so a notification between the hand-over and the evaluation made the step look every {@code p}
 * up again. The predicate was then evaluated for each one of them and each was accepted, so a query for a
 * single word counted all {@code p} elements of the collection. It was seen as
 * {@code analyzers-diacritics.xql:187 expected 1 but was 16} when XQSuite files ran in parallel.
 *
 * <p>One compiled expression is evaluated repeatedly, as a function in a module is, while another thread sends
 * update notifications back to back, as the stores and removes of other test files do.</p>
 */
public class FtQueryUpdateNotificationTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String COLLECTION = "ft-query-update-notification";
    private static final int WORDS = 16;
    private static final int ROUNDS = 400;
    private static final int ATTEMPTS = 30;

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index><lucene><text qname="p"/></lucene></index>
            </collection>""";

    @BeforeAll
    public static void storeFixture() throws XMLDBException {
        final CollectionManagementService cms =
                existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        final Collection collection = cms.createCollection(COLLECTION);
        collection.getService(IndexQueryService.class).configureCollection(CONFIGURATION);

        final StringBuilder xml = new StringBuilder("<test>");
        for (int i = 0; i < WORDS; i++) {
            xml.append("<p>").append(word(i)).append("</p>");
        }
        xml.append("</test>");
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("xmldb:store('/db/" + COLLECTION + "', 'test.xml', " + xml + ")");
    }

    @AfterAll
    public static void removeFixture() throws XMLDBException {
        final CollectionManagementService cms =
                existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(COLLECTION);
    }

    private static String word(final int i) {
        return "word" + (char) ('a' + i) + (char) ('a' + i);
    }

    @Test
    public void narrowedSetSurvivesUpdateNotifications() throws Exception {
        final StringBuilder words = new StringBuilder();
        for (int i = 0; i < WORDS; i++) {
            words.append(i > 0 ? ", " : "").append('"').append(word(i)).append('"');
        }
        // every word occurs in exactly one p; report the counts that are not 1
        final String query = """
                declare function local:hits($term as xs:string) {
                    count(collection('/db/%s')//p[ft:query(., $term)])
                };
                for $round in 1 to %d
                for $term in (%s)
                let $n := local:hits($term)
                where $n ne 1
                return $n
                """.formatted(COLLECTION, ROUNDS, words);

        // without notifications every count is right
        final XQueryService quiet = existEmbeddedServer.getRoot().getService(XQueryService.class);
        assertEquals(0, quiet.query(query).getSize(), "wrong counts without any update notification");

        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        // an update notification is what a store or a remove of any document sends to every listener
        final Thread notifier = new Thread(() -> {
            try {
                final BrokerPool pool = BrokerPool.getInstance();
                while (!stop.get()) {
                    pool.getNotificationService().notifyUpdate(null, UpdateListener.ADD);
                }
            } catch (final Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "notifier");
        notifier.start();

        long wrong = 0;
        String firstWrong = null;
        try {
            final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
            for (int attempt = 0; attempt < ATTEMPTS && wrong == 0; attempt++) {
                final ResourceSet rs = xqs.query(query);
                wrong += rs.getSize();
                if (rs.getSize() > 0) {
                    firstWrong = rs.getResource(0).getContent().toString();
                }
            }
        } finally {
            stop.set(true);
            notifier.join();
        }

        assertNull(failure.get(), () -> "notifier failed: " + failure.get());
        assertEquals(0, wrong, "a query for one word counted " + firstWrong + " p elements, not 1");
    }
}
