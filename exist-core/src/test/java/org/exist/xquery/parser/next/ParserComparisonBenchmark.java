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
import org.exist.xquery.Expression;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.XQueryTreeParser;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.StringReader;

/**
 * Side-by-side parse-only comparison: hand-written RD parser vs ANTLR 2.
 *
 * <p>"Parse-only" means: from source string to Expression tree.
 * For ANTLR, that's lexer + parser (AST) + tree walker (Expression).
 * For RD, that's lexer + parser (Expression directly).</p>
 *
 * <p>Run with:
 * {@code mvn test -pl exist-core -Dtest=ParserComparisonBenchmark
 *   -Ddependency-check.skip=true -Ddocker=false}</p>
 */
public class ParserComparisonBenchmark {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final int WARMUP = 2_000;
    private static final int MEASURED = 10_000;
    /** Iterations for slow queries (where ANTLR alone takes ~1ms+ per parse). */
    private static final int LARGE_WARMUP = 50;
    private static final int LARGE_MEASURED = 200;

    /** Queries marked with isXq4=true require xquery version "4.0" context. */
    private static final Query[] QUERIES = {
            new Query("Simple expr",
                    "1 + 2 * 3", false),
            new Query("Path with predicate",
                    "//book[author = 'Smith']/title", false),
            new Query("Simple FLWOR",
                    "for $x in 1 to 10 return $x * 2", false),
            new Query("Full FLWOR",
                    "for $x in 1 to 100 " +
                            "let $y := $x * 2 " +
                            "where $x > 50 " +
                            "order by $y descending " +
                            "return $y", false),
            new Query("Complex FLWOR",
                    "for $x in 1 to 100 " +
                            "let $y := $x * 2 " +
                            "let $z := $x mod 3 " +
                            "where $x > 10 and $x < 90 " +
                            "order by $z ascending, $y descending " +
                            "count $pos " +
                            "return $pos || ':' || string($y)", false),
            new Query("Nested if+FLWOR",
                    "let $data := (1, 2, 3, 4, 5) " +
                            "return " +
                            "  if (count($data) > 3) " +
                            "  then for $x in $data where $x > 2 return $x * $x " +
                            "  else ()", false),
            new Query("Typeswitch",
                    "typeswitch (42) " +
                            "case xs:string return 'string' " +
                            "case xs:integer return 'integer' " +
                            "case xs:double return 'double' " +
                            "default return 'other'", false),
            new Query("XQUF transform",
                    "copy $c := <root><item>old</item></root> " +
                            "modify (replace value of node $c/item with 'new', " +
                            "insert node <extra/> into $c) " +
                            "return $c", false),
            new Query("Arrow chain",
                    "(1, 2, 3, 4, 5) => sum() => string()", false),
            new Query("Element constructor",
                    "<book id='1'><title>Foo</title><author>{$a}</author></book>", false),
            new Query("Function decl",
                    "declare function local:fact($n as xs:integer) as xs:integer { " +
                            "if ($n <= 1) then 1 else $n * local:fact($n - 1) }; " +
                            "local:fact(10)", false),
            new Query("Large query (50 fns)", buildLargeQuery(50), false, true),
            new Query("Large query (200 fns)", buildLargeQuery(200), false, true),
    };

    /** Builds a synthetic query with N function declarations + a body. */
    private static String buildLargeQuery(final int n) {
        final StringBuilder sb = new StringBuilder(n * 200);
        for (int i = 0; i < n; i++) {
            sb.append("declare function local:f").append(i)
                    .append("($x as xs:integer, $y as xs:string) as xs:string { ")
                    .append("let $z := $x * 2 + ").append(i).append(" ")
                    .append("let $w := concat($y, '_', string($z)) ")
                    .append("return if ($z mod 2 = 0) then upper-case($w) else lower-case($w) ")
                    .append("};\n");
        }
        sb.append("local:f0(1, 'hello')");
        return sb.toString();
    }

    private record Query(String label, String source, boolean isXq4, boolean isLarge) {
        Query(String label, String source, boolean isXq4) { this(label, source, isXq4, false); }
    }

    @Test
    public void compareParsers() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            System.out.println();
            System.out.println("=========================================================================");
            System.out.println("  Parser comparison: RD (hand-written) vs ANTLR 2 (parse-only)");
            System.out.println("  Warmup: " + WARMUP + " iters, Measured: " + MEASURED + " iters");
            System.out.println("=========================================================================");
            System.out.printf("%-22s %12s %12s %12s%n", "Query", "ANTLR (μs)", "RD (μs)", "Speedup");
            System.out.println("-------------------------------------------------------------------------");

            for (final Query q : QUERIES) {
                final double antlrMicros = benchAntlr(pool, q);
                final double rdMicros = benchRd(pool, q);
                final double speedup = antlrMicros / rdMicros;
                System.out.printf("%-22s %12.2f %12.2f %11.2fx%n",
                        q.label, antlrMicros, rdMicros, speedup);
            }
            System.out.println("=========================================================================");
        }
    }

    private double benchRd(final BrokerPool pool, final Query q) throws Exception {
        final int warmup = q.isLarge ? LARGE_WARMUP : WARMUP;
        final int measured = q.isLarge ? LARGE_MEASURED : MEASURED;
        // Warmup
        for (int i = 0; i < warmup; i++) {
            final XQueryContext ctx = new XQueryContext(pool);
            if (q.isXq4) ctx.setXQueryVersion(40);
            try {
                new XQueryParser(ctx, q.source).parse();
            } finally {
                ctx.reset();
            }
        }
        // Measure
        final long start = System.nanoTime();
        for (int i = 0; i < measured; i++) {
            final XQueryContext ctx = new XQueryContext(pool);
            if (q.isXq4) ctx.setXQueryVersion(40);
            try {
                new XQueryParser(ctx, q.source).parse();
            } finally {
                ctx.reset();
            }
        }
        final long elapsed = System.nanoTime() - start;
        return (elapsed / 1_000.0) / measured;
    }

    private double benchAntlr(final BrokerPool pool, final Query q) throws Exception {
        final String source = q.isXq4 ? "xquery version \"4.0\";\n" + q.source : q.source;
        final int warmup = q.isLarge ? LARGE_WARMUP : WARMUP;
        final int measured = q.isLarge ? LARGE_MEASURED : MEASURED;
        // Warmup
        for (int i = 0; i < warmup; i++) {
            parseAntlr(pool, source);
        }
        // Measure
        final long start = System.nanoTime();
        for (int i = 0; i < measured; i++) {
            parseAntlr(pool, source);
        }
        final long elapsed = System.nanoTime() - start;
        return (elapsed / 1_000.0) / measured;
    }

    private void parseAntlr(final BrokerPool pool, final String source) throws Exception {
        final XQueryContext ctx = new XQueryContext(pool);
        try {
            final org.exist.xquery.parser.XQueryLexer lexer =
                    new org.exist.xquery.parser.XQueryLexer(ctx, new StringReader(source));
            final org.exist.xquery.parser.XQueryParser parser =
                    new org.exist.xquery.parser.XQueryParser(lexer);
            parser.xpath();
            if (parser.foundErrors()) {
                throw new RuntimeException("ANTLR parse error: " + parser.getErrorMessage());
            }
            final XQueryAST ast = (XQueryAST) parser.getAST();
            final XQueryTreeParser treeParser = new XQueryTreeParser(ctx);
            final PathExpr path = new PathExpr(ctx);
            treeParser.xpath(ast, path);
            if (treeParser.foundErrors()) {
                throw new RuntimeException("ANTLR tree-walk error: " + treeParser.getErrorMessage());
            }
        } finally {
            ctx.reset();
        }
    }
}
