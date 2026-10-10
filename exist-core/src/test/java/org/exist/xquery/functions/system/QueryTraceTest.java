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
package org.exist.xquery.functions.system;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The statistics of {@code system:query-trace} belong to the query that records them: another query that runs at the
 * same time neither adds to them nor sees them, and neither switches the database-wide tracing on.
 */
public class QueryTraceTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String PROLOG = """
            import module namespace system = "http://exist-db.org/xquery/system";
            import module namespace util = "http://exist-db.org/xquery/util";
            declare namespace stats = "http://exist-db.org/xquery/profiling";
            """;

    /** records 40 calls of fn:abs while another query runs, and reports what its trace holds */
    private static final String RECORDING_QUERY = PROLOG + """
            let $_ := system:clear-query-trace()
            let $_ := system:enable-query-tracing(true())
            let $_ := util:wait(1500)
            let $calls := for $i in 1 to 40 return abs($i)
            let $trace := system:query-trace()
            let $_ := system:enable-query-tracing(false())
            return (
                "abs=" || sum($trace//stats:function[@name = "abs"]/@calls),
                "upper-case=" || count($trace//stats:function[@name = "upper-case"]),
                "calls-made=" || count($calls)
            )
            """;

    /** calls fn:upper-case 30 times without tracing, and reports what its trace holds */
    private static final String BYSTANDER_QUERY = PROLOG + """
            let $calls := for $i in 1 to 30 return upper-case("x")
            let $trace := system:query-trace()
            return (
                "recorded=" || count($trace/*),
                "calls-made=" || count($calls),
                "database-wide=" || system:tracing-enabled()
            )
            """;

    @Test
    public void tracingOneQueryRecordsNothingForAnotherAndLeavesTheDatabaseWideTraceOff() throws Exception {
        final CompletableFuture<String> recording = CompletableFuture.supplyAsync(() -> run(RECORDING_QUERY));

        // the first query has tracing on and is waiting now: run the second one inside that window
        TimeUnit.MILLISECONDS.sleep(400);
        final String bystander = run(BYSTANDER_QUERY);

        assertEquals("recorded=0 calls-made=30 database-wide=false", bystander,
                "the query that never switched tracing on, while another one had it on");
        assertEquals("abs=40 upper-case=0 calls-made=40", recording.get(30, TimeUnit.SECONDS),
                "the query that switched tracing on, while another one made calls");
        assertEquals("false", run(PROLOG + "string(system:tracing-enabled())"),
                "database-wide tracing afterwards");
    }

    @Test
    public void clearingTheTraceOfOneQueryKeepsTheStatisticsOfAnother() throws Exception {
        final String clearingQuery = PROLOG + """
                let $_ := system:enable-query-tracing(true())
                let $_ := util:wait(1200)
                let $_ := system:clear-query-trace()
                let $calls := for $i in 1 to 10 return abs($i)
                let $trace := system:query-trace()
                let $_ := system:enable-query-tracing(false())
                return "abs=" || sum($trace//stats:function[@name = "abs"]/@calls) || " " || count($calls)
                """;
        final String keepingQuery = PROLOG + """
                let $_ := system:clear-query-trace()
                let $_ := system:enable-query-tracing(true())
                let $calls := for $i in 1 to 25 return abs($i)
                let $_ := util:wait(1800)
                let $trace := system:query-trace()
                let $_ := system:enable-query-tracing(false())
                return "abs=" || sum($trace//stats:function[@name = "abs"]/@calls) || " " || count($calls)
                """;

        final CompletableFuture<String> keeping = CompletableFuture.supplyAsync(() -> run(keepingQuery));
        TimeUnit.MILLISECONDS.sleep(100);
        final CompletableFuture<String> clearing = CompletableFuture.supplyAsync(() -> run(clearingQuery));

        // the second query clears its own trace while the first one is waiting and still has its 25 calls recorded
        assertEquals("abs=10 10", clearing.get(30, TimeUnit.SECONDS), "the query that cleared its trace");
        assertEquals("abs=25 25", keeping.get(30, TimeUnit.SECONDS), "the query whose trace another one cleared");
    }

    @Test
    public void aQueryThatLeftTracingOnDoesNotRecordInItsNextExecution() throws Exception {
        assertNextExecutionRecordsNothing("""
                let $_ := if ($enable) then system:enable-query-tracing(true()) else ()
                """, "the execution that switched tracing on and never off");
    }

    @Test
    public void aQueryThatSwitchedTracingOffLeavesNothingForItsNextExecution() throws Exception {
        assertNextExecutionRecordsNothing("""
                let $_ := if ($enable) then system:enable-query-tracing(true()) else ()
                let $first := abs(-1)
                let $_ := if ($enable) then system:enable-query-tracing(false()) else ()
                """, "the execution that switched tracing on and off again");
    }

    /**
     * Executes one compiled query twice, as the query pool does: the first time it records at least one call,
     * the second time it does not switch tracing on and has to find an empty trace.
     */
    private static void assertNextExecutionRecordsNothing(final String firstPart, final String firstExecution)
            throws Exception {
        final String query = PROLOG + "declare variable $enable external;\n" + firstPart + """
                let $calls := for $i in 1 to 4 return abs($i)
                return count(system:query-trace()//stats:function[@name = "abs"])
                """;

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), query);
            try {
                final XQueryContext context = compiled.getContext();

                context.declareVariable("enable", new BooleanValue(null, true));
                assertEquals("1", xquery.execute(broker, compiled, null).getStringValue(), firstExecution);

                context.declareVariable("enable", new BooleanValue(null, false));
                assertEquals("0", xquery.execute(broker, compiled, null).getStringValue(),
                        "the next execution of the same query, which did not switch tracing on");
            } finally {
                compiled.reset();
            }
        }
    }

    private static String run(final String query) {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = pool.getXQueryService().execute(broker, query, null);
            final StringBuilder items = new StringBuilder();
            for (final SequenceIterator it = result.iterate(); it.hasNext(); ) {
                if (!items.isEmpty()) {
                    items.append(' ');
                }
                items.append(it.nextItem().getStringValue());
            }
            return items.toString();
        } catch (final EXistException | PermissionDeniedException | XPathException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
