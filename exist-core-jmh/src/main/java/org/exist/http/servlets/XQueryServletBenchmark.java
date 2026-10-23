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
package org.exist.http.servlets;

import org.exist.collections.Collection;
import org.exist.security.PermissionFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Measures the per-request cost of {@link XQueryServlet#process}, the entry point every
 * {@code *.xq|xql|xqy|xquery} request reaches through the URL-rewriting controller.
 *
 * <p>The query is trivial and served from the compiled-query pool after the first invocation, so
 * the measurement is dominated by the servlet's own work: path translation, user resolution,
 * source resolution, option handling, context preparation, execution set-up/clean-up and
 * serialization. {@code sourceKind} selects one of the three ways {@code process()} resolves the
 * query source:</p>
 * <ul>
 *   <li>{@code sourceAttribute} — inline query in the {@code xquery.source} request attribute;</li>
 *   <li>{@code urlAttribute} — stored query named by the {@code xquery.url} request attribute (a
 *       controller {@code <forward>} to a database resource);</li>
 *   <li>{@code databaseFallback} — no disk path for the request, so the request path is looked up
 *       in the database (<a href="https://github.com/eXist-db/exist/issues/6615">#6615</a>).</li>
 * </ul>
 *
 * <p>Companion guard for the refactoring that split {@code process()} into phase methods to bring
 * its NPath complexity under 200: the split must be performance neutral. Run with {@code -prof gc}
 * and compare both {@code µs/op} and {@code gc.alloc.rate.norm} (bytes/op).</p>
 *
 * <p>Request and response are the plain stubs from exist-core's test-jar
 * ({@link StubHttpServletRequest}, {@link StubHttpServletResponse}), created fresh per invocation
 * like real traffic, so no mocking framework sits in the measured path.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class XQueryServletBenchmark {

    private static final String CONTEXT = "/exist";
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/apps/xqueryservlet-bench");
    private static final XmldbURI STORED_QUERY = COLLECTION.append("hello.xql");
    private static final String QUERY = "<ok/>";

    @Param({"sourceAttribute", "urlAttribute", "databaseFallback"})
    public String sourceKind;

    private ExistEmbeddedServer server;
    private XQueryServlet servlet;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        final Properties configProperties = new Properties();
        // Keep JMH forks clean: request immediate BrokerPool shutdown so Quartz
        // workers do not linger past benchmark completion.
        configProperties.put("wait-before-shutdown", 0L);
        server = new ExistEmbeddedServer(configProperties, true, true);
        server.startDb();
        storeQuery(server.getBrokerPool());

        servlet = new XQueryServlet();
        servlet.init(StubServletConfig.create());

        // fail fast instead of measuring an error page
        final StubHttpServletResponse response = new StubHttpServletResponse();
        servlet.process(newRequest(), response);
        if (response.getStatus() != StubHttpServletResponse.SC_OK || !response.getBodyAsString().contains(QUERY)) {
            throw new IllegalStateException("Benchmark request for " + sourceKind + " failed with status "
                    + response.getStatus() + ": " + response.getBodyAsString());
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (servlet != null) {
            servlet.destroy();
        }
        if (server != null) {
            server.stopDb();
        }
    }

    /**
     * @return the number of bytes written to the response (consumed by JMH to prevent dead-code elimination)
     */
    @Benchmark
    public int process() throws Exception {
        final StubHttpServletResponse response = new StubHttpServletResponse();
        servlet.process(newRequest(), response);
        return response.getBodySize();
    }

    private StubHttpServletRequest newRequest() {
        return switch (sourceKind) {
            case "sourceAttribute" -> new StubHttpServletRequest(CONTEXT, CONTEXT + "/apps/bench/inline.xql")
                    .attribute(XQueryServlet.ATTR_XQUERY_SOURCE, QUERY);
            case "urlAttribute" -> new StubHttpServletRequest(CONTEXT, CONTEXT + "/apps/bench/forwarded.xql")
                    .attribute(XQueryServlet.ATTR_XQUERY_URL, "xmldb:exist://" + STORED_QUERY);
            case "databaseFallback" -> new StubHttpServletRequest(CONTEXT, CONTEXT + STORED_QUERY);
            default -> throw new IllegalArgumentException("Unknown sourceKind: " + sourceKind);
        };
    }

    private static void storeQuery(final BrokerPool pool) throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
                broker.storeDocument(transaction, STORED_QUERY.lastSegment(),
                        new StringInputSource(QUERY.getBytes(UTF_8)), MimeType.XQUERY_TYPE, collection);
            }
            PermissionFactory.chmod_str(broker, transaction, STORED_QUERY, Optional.of("rwxr-xr-x"), Optional.empty());
            transaction.commit();
        }
    }
}
