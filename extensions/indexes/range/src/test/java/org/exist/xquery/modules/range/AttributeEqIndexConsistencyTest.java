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
package org.exist.xquery.modules.range;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for <a href="https://github.com/eXist-db/exist/issues/3964">#3964</a>:
 * an attribute predicate {@code [@x eq 'val']} on a {@code collection(...)} step must return
 * the same count as the explicit-string variant {@code [@x/string() eq 'val']}, regardless
 * of whether a range index is defined on the attribute.
 *
 * <p>The issue reported that {@code @contrib-id-type eq 'jb-contributor-id'} returned 0
 * results because the optimizer rewrote it to {@code range:eq(@contrib-id-type, ...)} when
 * the range module was active, but no index was configured on the attribute itself. These
 * tests exercise the rewrite path (the rewrite still happens — confirmed by AST inspection)
 * and ensure the runtime fallback to the original {@code GeneralComparison} produces correct
 * results.
 */
public class AttributeEqIndexConsistencyTest {

    private static final String COLLECTION_NAME = "i3964-attr-eq-test";

    /**
     * Collection.xconf that defines a range index on an UNRELATED element ({@code <article-title>}),
     * so the range index module is active for this collection but {@code @contrib-id-type} has no
     * index. This mirrors the configuration in #3964 where the optimizer rewrote {@code @x eq 'val'}
     * to {@code range:eq(@x, ...)} despite no matching index.
     */
    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <range>
                        <create qname="article-title" type="xs:string"/>
                    </range>
                </index>
            </collection>""";

    private static final String SAMPLE_DOC = """
            <article>
                <front>
                    <article-meta>
                        <contrib-group>
                            <contrib>
                                <contrib-id contrib-id-type="jb-contributor-id">42</contrib-id>
                            </contrib>
                        </contrib-group>
                    </article-meta>
                </front>
            </article>""";

    private static final int DOC_COUNT = 5;

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static XmldbURI collectionUri;

    @BeforeClass
    public static void setUp() throws EXistException, PermissionDeniedException, LockException,
            TriggerException, SAXException, CollectionConfigurationException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        collectionUri = XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION_NAME);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(txn, collectionUri);
            broker.saveCollection(txn, col);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, col, COLLECTION_CONFIG);

            for (int i = 1; i <= DOC_COUNT; i++) {
                broker.storeDocument(txn, XmldbURI.create("article-" + i + ".xml"),
                        new StringInputSource(SAMPLE_DOC), MimeType.XML_TYPE, col);
            }
            broker.reindexCollection(txn, col.getURI());
            pool.getTransactionManager().commit(txn);
        }
    }

    @AfterClass
    public static void tearDown() throws EXistException, PermissionDeniedException, LockException,
            TriggerException, IOException {
        if (collectionUri == null) {
            return;
        }
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = transact.beginTransaction();
             final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            if (collection != null) {
                broker.removeCollection(txn, collection);
            }
            transact.commit(txn);
        }
    }

    /**
     * Reduced reproducer from issue #3964: both shapes must agree.
     */
    @Test
    public void attributeEqMatchesStringEqOnCollectionWithoutIndex() throws EXistException, PermissionDeniedException, XPathException {
        final long shapeA = count(
                "collection('/db/" + COLLECTION_NAME + "')//contrib-id[@contrib-id-type eq 'jb-contributor-id']");
        final long shapeB = count(
                "collection('/db/" + COLLECTION_NAME + "')//contrib-id[@contrib-id-type/string() eq 'jb-contributor-id']");

        assertEquals("Sanity: " + DOC_COUNT + " articles stored", DOC_COUNT, shapeB);
        assertEquals(
                "Shape A (@x eq 'val') must return the same count as Shape B (@x/string() eq 'val'); "
                        + "issue #3964 — without this fix Shape A returned 0 because the optimizer rewrote "
                        + "to range:eq even when no range index is configured.",
                shapeB, shapeA);
    }

    /**
     * Same check using {@code =} (general comparison).
     */
    @Test
    public void attributeGeneralEqMatchesStringEqOnCollectionWithoutIndex() throws EXistException, PermissionDeniedException, XPathException {
        final long shapeA = count(
                "collection('/db/" + COLLECTION_NAME + "')//contrib-id[@contrib-id-type = 'jb-contributor-id']");
        final long shapeB = count(
                "collection('/db/" + COLLECTION_NAME + "')//contrib-id[@contrib-id-type/string() = 'jb-contributor-id']");

        assertEquals("Sanity: " + DOC_COUNT + " articles stored", DOC_COUNT, shapeB);
        assertEquals("Shape A (general '=') must agree with Shape B", shapeB, shapeA);
    }

    /**
     * Same check via {@code fn:doc} — establishes the baseline that already worked in #3964
     * and guards against regressing it.
     */
    @Test
    public void attributeEqMatchesStringEqOnDocWithoutIndex() throws EXistException, PermissionDeniedException, XPathException {
        final String docPath = "/db/" + COLLECTION_NAME + "/article-1.xml";
        final long shapeA = count("doc('" + docPath + "')//contrib-id[@contrib-id-type eq 'jb-contributor-id']");
        final long shapeB = count("doc('" + docPath + "')//contrib-id[@contrib-id-type/string() eq 'jb-contributor-id']");

        assertEquals("Sanity: 1 contrib-id in one document", 1L, shapeB);
        assertEquals("Shape A (doc) must agree with Shape B", shapeB, shapeA);
    }

    private long count(final String xpath) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext ctx = new XQueryContext(pool);
            final Sequence result = xquery.execute(broker,
                    xquery.compile(ctx, "declare option exist:optimize 'enable=yes'; count(" + xpath + ")"),
                    null);
            return ((org.exist.xquery.value.IntegerValue) result.itemAt(0)).getLong();
        }
    }
}
