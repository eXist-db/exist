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
package org.exist.xquery.parser.next;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XQueryContext;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Micro-benchmark for the hand-written parser.
 * Measures parse time (lexer + parser) for increasingly complex queries.
 *
 * <p>Target: FLWOR parsing ≤ 45μs (develop baseline with ANTLR 2).</p>
 */
public class ParserBenchmark {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String SIMPLE_EXPR = "1 + 2 * 3";

    private static final String SIMPLE_FLWOR =
            "for $x in 1 to 10 return $x * 2";

    private static final String FULL_FLWOR =
            "for $x in 1 to 100 " +
            "let $y := $x * 2 " +
            "where $x > 50 " +
            "order by $y descending " +
            "return $y";

    private static final String COMPLEX_FLWOR =
            "for $x in 1 to 100 " +
            "let $y := $x * 2 " +
            "let $z := $x mod 3 " +
            "where $x > 10 and $x < 90 " +
            "order by $z ascending, $y descending " +
            "count $pos " +
            "return $pos || ':' || string($y)";

    private static final String NESTED_EXPR =
            "let $data := (1, 2, 3, 4, 5) " +
            "return " +
            "  if (count($data) > 3) " +
            "  then for $x in $data where $x > 2 return $x * $x " +
            "  else ()";

    private static final String TYPESWITCH_EXPR =
            "typeswitch (42) " +
            "case xs:string return 'string' " +
            "case xs:integer return 'integer' " +
            "case xs:double return 'double' " +
            "default return 'other'";

    private static final String XQUF_TRANSFORM =
            "copy $c := <root><item>old</item></root> " +
            "modify (replace value of node $c/item with 'new', " +
            "insert node <extra/> into $c) " +
            "return $c";

    private static final String XQFT_CONTAINS =
            "'XML database engine' contains text 'XML' ftand 'database' " +
            "using stemming using language 'en'";

    private static final String XQ4_PIPELINE =
            "(1, 2, 3, 4, 5) -> count() + " +
            "('hello', 'world') =!> upper-case() => string-join(' ')";

    private static final int WARMUP = 10_000;
    private static final int MEASURED = 50_000;

    @Test
    public void benchmarkAll() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            System.out.println("\n=== Parser Benchmarks (lexer + parse + Expression tree) ===");
            runParserBenchmark(pool, "Simple expr (1+2*3)", SIMPLE_EXPR);
            runParserBenchmark(pool, "Simple FLWOR", SIMPLE_FLWOR);
            runParserBenchmark(pool, "Full FLWOR (where+order)", FULL_FLWOR);
            runParserBenchmark(pool, "Complex FLWOR", COMPLEX_FLWOR);
            runParserBenchmark(pool, "Nested if+FLWOR", NESTED_EXPR);
            runParserBenchmark(pool, "Typeswitch", TYPESWITCH_EXPR);
            runParserBenchmark(pool, "XQUF transform", XQUF_TRANSFORM);
            runParserBenchmark(pool, "XQFT contains text", XQFT_CONTAINS);
            runParserBenchmark(pool, "XQ4 pipeline+arrow", XQ4_PIPELINE);
        }
    }

    private void runParserBenchmark(final BrokerPool pool, final String label, final String query)
            throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            final XQueryContext ctx = new XQueryContext(pool);
            try {
                new XQueryParser(ctx, query).parseExpression();
            } finally {
                ctx.reset();
            }
        }

        // Measure
        final long start = System.nanoTime();
        for (int i = 0; i < MEASURED; i++) {
            final XQueryContext ctx = new XQueryContext(pool);
            try {
                new XQueryParser(ctx, query).parseExpression();
            } finally {
                ctx.reset();
            }
        }
        final long elapsed = System.nanoTime() - start;
        final double avgMicros = (elapsed / 1_000.0) / MEASURED;

        System.out.printf("  %-30s  %.1f µs  (%d chars)%n", label, avgMicros, query.length());
    }
}
