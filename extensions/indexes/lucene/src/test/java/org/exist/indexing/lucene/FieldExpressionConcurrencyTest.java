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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field and facet expressions, and field conditions, evaluated by several brokers at the same time.
 * <p>
 * Reindexing a collection takes only READ locks, so reindexes of sibling collections under one index configuration
 * run at the same time, each evaluating the configuration's expressions. When they shared one compiled query, most of
 * the reindexes failed and the others stored values computed from other nodes. See
 * <a href="https://github.com/eXist-db/exist/issues/7039">#7039</a>.
 */
public class FieldExpressionConcurrencyTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final int COLLECTIONS = 5;
    private static final int DOCUMENTS = 200;
    private static final int ITEMS = 10;
    private static final int ROUNDS = 3;

    private static final String SETUP = """
            xquery version "3.1";
            declare variable $collections external;
            declare variable $documents external;
            declare variable $items external;

            xmldb:create-collection("/db", "field-concurrency"),
            xmldb:store("/db/field-concurrency", "index.xql", '
                xquery version "3.1";
                module namespace idx="http://exist-db.org/test/field-concurrency";
                declare function idx:key($item as element(item)) as xs:string {
                    util:document-name($item) || "#" || $item/@n
                };
                declare function idx:group($item as element(item)) as xs:string {
                    "g" || (xs:integer($item/@n) mod 3)
                };', "application/xquery"),
            xmldb:create-collection("/db/system/config/db", "field-concurrency"),
            xmldb:store("/db/system/config/db/field-concurrency", "collection.xconf",
                <collection xmlns="http://exist-db.org/collection-config/1.0">
                    <index>
                        <lucene>
                            <module uri="http://exist-db.org/test/field-concurrency" prefix="idx" at="index.xql"/>
                            <text qname="item">
                                <field name="key" expression="idx:key(.)"/>
                                <field name="inline-key" expression="util:document-name(.) || '#' || @n"/>
                                <field name="even" if="xs:integer(@n) mod 2 = 0" expression="idx:key(.)"/>
                                <facet dimension="group" expression="idx:group(.)"/>
                            </text>
                        </lucene>
                    </index>
                </collection>),
            for $c in 1 to $collections
            let $collection := xmldb:create-collection("/db/field-concurrency", "c" || $c)
            for $d in 1 to $documents
            return xmldb:store($collection, "d" || $d || ".xml",
                <doc>{ for $n in 1 to $items return <item n="{$n}">item {$c} {$d} {$n}</item> }</doc>)
            """;

    /**
     * The number of items whose fields and facet are what their expressions give for them, followed by the number of
     * items in the index.
     */
    private static final String CHECK = """
            xquery version "3.1";
            let $hits := ft:query(collection("/db/field-concurrency")//item, (),
                map { "fields": ("key", "inline-key", "even") })
            let $correct :=
                for $hit in $hits
                let $key := util:document-name($hit) || "#" || $hit/@n
                where ft:field($hit, "key") = $key
                    and ft:field($hit, "inline-key") = $key
                    and (if (xs:integer($hit/@n) mod 2 = 0) then ft:field($hit, "even") = $key
                         else empty(ft:field($hit, "even")))
                return $hit
            let $facets := ft:facets($hits, "group", ())
            return (
                count($correct),
                count($hits),
                $facets?g0, $facets?g1, $facets?g2
            )
            """;

    @BeforeAll
    static void setUp() throws XMLDBException {
        final EXistXQueryService service = existEmbeddedServer.getRoot().getService(EXistXQueryService.class);
        service.declareVariable("collections", COLLECTIONS);
        service.declareVariable("documents", DOCUMENTS);
        service.declareVariable("items", ITEMS);
        service.query(SETUP);
        service.clearVariables();
    }

    @Test
    void concurrentReindexesOfSiblingCollections() throws Exception {
        assertIndexComplete("after store");

        final ExecutorService executor = Executors.newFixedThreadPool(COLLECTIONS);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                final CountDownLatch start = new CountDownLatch(1);
                final List<Future<String>> reindexes = new ArrayList<>();
                for (int c = 1; c <= COLLECTIONS; c++) {
                    final String collection = "/db/field-concurrency/c" + c;
                    final Callable<String> reindex = () -> {
                        start.await();
                        final EXistXQueryService service = existEmbeddedServer.getRoot().getService(EXistXQueryService.class);
                        return service.query("xmldb:reindex('" + collection + "')").getResource(0).getContent().toString();
                    };
                    reindexes.add(executor.submit(reindex));
                }
                start.countDown();
                for (final Future<String> reindex : reindexes) {
                    assertEquals("true", reindex.get(2, TimeUnit.MINUTES), "round " + round);
                }
                assertIndexComplete("after concurrent reindex round " + round);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
        }
    }

    private static void assertIndexComplete(final String when) throws XMLDBException {
        final int items = COLLECTIONS * DOCUMENTS * ITEMS;
        final ResourceSet result = existEmbeddedServer.getRoot().getService(EXistXQueryService.class).query(CHECK);
        assertEquals(items, Integer.parseInt(result.getResource(1).getContent().toString()), when + ": indexed items");
        assertEquals(items, Integer.parseInt(result.getResource(0).getContent().toString()), when + ": items with correct fields");
        // n mod 3 for n in 1..10: 0 for 3, 6, 9; 1 for 1, 4, 7, 10; 2 for 2, 5, 8
        final int documents = COLLECTIONS * DOCUMENTS;
        assertEquals(3 * documents, Integer.parseInt(result.getResource(2).getContent().toString()), when + ": facet g0");
        assertEquals(4 * documents, Integer.parseInt(result.getResource(3).getContent().toString()), when + ": facet g1");
        assertEquals(3 * documents, Integer.parseInt(result.getResource(4).getContent().toString()), when + ": facet g2");
    }
}
