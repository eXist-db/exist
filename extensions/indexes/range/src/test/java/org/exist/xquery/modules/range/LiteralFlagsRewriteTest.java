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

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A literal flag the range index has no equivalent of keeps {@code fn:matches} out of the rewrite: no lookup
 * is created for it, rather than one that steps aside when it runs.
 */
public class LiteralFlagsRewriteTest {

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <range>
                        <create qname="item" type="xs:string"/>
                    </range>
                </index>
            </collection>
            """;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeAll
    static void setUp() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XmldbURI uri = XmldbURI.ROOT_COLLECTION_URI.append("literal-flags");
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(txn, uri);
            broker.saveCollection(txn, col);
            pool.getConfigurationManager().addConfiguration(txn, broker, col, COLLECTION_CONFIG);
            broker.storeDocument(txn, XmldbURI.create("a.xml"), new StringInputSource("<wrap><item>hit</item></wrap>"), MimeType.XML_TYPE, col);
            broker.reindexCollection(txn, col.getURI());
            pool.getTransactionManager().commit(txn);
        }
    }

    private static String dump(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), query);
            final StringWriter out = new StringWriter();
            compiled.dump(out);
            return out.toString();
        }
    }

    private static void assertNotRewritten(final String predicate) throws Exception {
        final String tree = dump("collection('/db/literal-flags')//item[" + predicate + "]");
        assertFalse(tree.contains("range:matches"), predicate + " was rewritten to a lookup:\n" + tree);
    }

    @Test
    void sFlagIsNotRewritten() throws Exception {
        assertNotRewritten("matches(., '^hit$', 's')");
    }

    @Test
    void mFlagIsNotRewritten() throws Exception {
        assertNotRewritten("matches(., '^hit$', 'm')");
    }

    @Test
    void xFlagIsNotRewritten() throws Exception {
        assertNotRewritten("matches(., '^hit$', 'x')");
    }

    @Test
    void iFlagWithAnotherIsNotRewritten() throws Exception {
        assertNotRewritten("matches(., '^HIT$', 'is')");
    }

    @Test
    void sFlagOnAnAttributeIsNotRewritten() throws Exception {
        assertNotRewritten("matches(@n, '^1$', 's')");
    }

    @Test
    void iFlagIsRewritten() throws Exception {
        final String tree = dump("collection('/db/literal-flags')//item[matches(., '^HIT$', 'i')]");
        assertTrue(tree.contains("range:matches"), tree);
    }

    @Test
    void noFlagsIsRewritten() throws Exception {
        final String tree = dump("collection('/db/literal-flags')//item[matches(., '^hit$')]");
        assertTrue(tree.contains("range:matches"), tree);
    }
}
