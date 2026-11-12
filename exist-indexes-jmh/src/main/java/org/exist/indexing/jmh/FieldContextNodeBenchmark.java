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
package org.exist.indexing.jmh;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XQuery;
import org.openjdk.jmh.annotations.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Storing a document whose indexed nodes lie far below {@code index-depth} and share an ancestor at a shallow level
 * (eXist-db/exist#6959). Each call stores the document again, so the cost measured is indexing it.
 *
 * <p>Three Lucene fields on {@code category}, one per run: {@code node} evaluates {@code local-name(.)}, which needs the
 * indexed node itself; {@code attribute} reads {@code @xml:id}, which needs the node loaded as well; {@code child}
 * reads the named child {@code catDesc}, which is answered from the structural index and needs no lookup. Doubling
 * {@code categories} should double the time of all three. Where the time of {@code node} or {@code attribute} grows
 * four times, every evaluation of the expression finds its node again by walking the document, which is the cost this
 * benchmark is meant to show; {@code child} is the control.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class FieldContextNodeBenchmark {

    @Param({"node", "attribute", "child"})
    public String field;

    @Param({"1000", "2000", "4000"})
    public int categories;

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private XQuery xquery;
    private String store;

    /**
     * Starts an embedded server and configures a Lucene index with one field on {@code category}.
     *
     * @throws Exception if the server cannot be started or the configuration cannot be stored
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();
        xquery = pool.getXQueryService();

        final String expression = switch (field) {
            case "node" -> "local-name(.)";
            case "attribute" -> "string(@xml:id)";
            default -> "catDesc";
        };
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            xquery.execute(broker, """
                    xmldb:create-collection('/db/system', 'config'), xmldb:create-collection('/db/system/config', 'db'),
                    xmldb:create-collection('/db/system/config/db', 'deep'), xmldb:create-collection('/db', 'deep'),
                    xmldb:store('/db/system/config/db/deep', 'collection.xconf',
                        <collection xmlns="http://exist-db.org/collection-config/1.0"><index><lucene>
                            <text qname="category"><field name="f" expression="%s"/></text>
                        </lucene></index></collection>)
                    """.formatted(expression), null);
        }
        store = """
                xmldb:store('/db/deep', 'taxonomy.xml',
                    <TEI><teiHeader><encodingDesc><classDecl><taxonomy>{
                        (1 to %d) ! <category xml:id="c{.}"><catDesc>Category {.}</catDesc></category>
                    }</taxonomy></classDecl></encodingDesc></teiHeader></TEI>)
                """.formatted(categories);
    }

    /**
     * Stops the embedded server.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        server.stopDb(true);
    }

    /**
     * Stores the document, which indexes every {@code category} and evaluates the field expression for it.
     *
     * @return the number of items the store returned, so that JMH cannot optimise the call away
     * @throws Exception if the store fails
     */
    @Benchmark
    public int storeAndIndex() throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return xquery.execute(broker, store, null).getItemCount();
        }
    }
}
