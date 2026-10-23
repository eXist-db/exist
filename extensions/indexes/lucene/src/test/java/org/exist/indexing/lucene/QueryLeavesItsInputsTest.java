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

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A query leaves the nodes it was given as they were: after evaluating a query over the nodes bound to {@code $c} and
 * {@code $d}, each of those nodes has the matches and the context chain it had before. An evaluation's matches and
 * context are carried by the nodes it creates, never written onto nodes it was handed.
 */
public class QueryLeavesItsInputsTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI COLLECTION = XmldbURI.create("/db/test-query-leaves-its-inputs-java");

    private static final String CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index><lucene><text qname="p"/></lucene></index>
            </collection>""";

    private static final String XML = """
            <body><div><p>alpha beta</p><p>gamma</p></div><div><p>delta</p></div></body>""";

    @BeforeAll
    public static void store() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, collection, CONFIG);
            broker.storeDocument(transaction, XmldbURI.create("doc.xml"), new StringInputSource(XML), MimeType.XML_TYPE,
                    collection);
            transact.commit(transaction);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // full text
            "$c[ft:query(., 'alpha')]",
            "$c[ft:query(., 'alpha')][ft:query(., 'beta')]",
            "for $n in $c where ft:query($n, 'alpha') return $n",
            "$d[p[ft:query(., 'alpha')]]",
            "$d[ft:query(p, 'alpha')]",
            "$c/self::p[ft:query(., 'alpha')]",
            "ft:query($c, 'alpha')",
            "$c[ft:query(., 'alpha')]/..",
            "$d/p[ft:query(., 'alpha')]",
            // predicates and steps
            "$c[1]",
            "$c[last()]",
            "$c[. = 'gamma']",
            "$d[p]",
            "$d[p = 'gamma']",
            "$d[not(p = 'gamma')]",
            "$d/p[. = 'gamma']",
            "$c/parent::div",
            "$c/..[p]",
            "$c/ancestor::body",
            "$d[p[. = $c[2]]]",
            // FLWOR
            "for $n in $c where $n = 'gamma' return $n",
            "for $n in $c return $n[. = 'gamma']",
            "let $x := $c return $x[2]",
            // set operations
            "$c | $d/p",
            "$c intersect $d/p",
            "$c except $d/p[1]",
            // functions, recursion
            "declare function local:f($n, $k) { if ($k = 0) then $n[p] else local:f($n[p], $k - 1) }; local:f($d, 3)",
            "declare function local:id($n) { $n }; local:id($c)[. = 'gamma']"
    })
    public void theInputNodesAreLeftAsTheyWere(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence c = xquery.execute(broker, "collection('" + COLLECTION + "')//p", null);
            final Sequence d = xquery.execute(broker, "collection('" + COLLECTION + "')//div", null);
            final List<String> before = snapshot(c, d);

            final XQueryContext context = new XQueryContext(pool);
            context.declareVariable("c", c);
            context.declareVariable("d", d);
            final CompiledXQuery compiled = xquery.compile(context,
                    "import module namespace ft = 'http://exist-db.org/xquery/lucene'; " + query);
            xquery.execute(broker, compiled, null);

            assertEquals(String.join("\n", before), String.join("\n", snapshot(c, d)));
        }
    }

    /** Each node's identity, matches and context chain, one line per node. */
    private static List<String> snapshot(final Sequence... sequences) throws Exception {
        final List<String> lines = new ArrayList<>();
        for (final Sequence sequence : sequences) {
            for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                final NodeProxy node = (NodeProxy) item;
                final StringBuilder line = new StringBuilder(node.getNodeId().toString()).append(" matches=[");
                for (Match m = node.getMatches(); m != null; m = m.getNextMatch()) {
                    line.append(m.getIndexId()).append(':').append(m).append(' ');
                }
                line.append("] context=[");
                for (ContextItem ci = node.getContext(); ci != null; ci = ci.getNextDirect()) {
                    line.append(ci.getContextId()).append(':').append(ci.getNode().getNodeId()).append(' ');
                }
                lines.add(line.append(']').toString());
            }
        }
        return lines;
    }
}
