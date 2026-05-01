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
package org.exist.xquery.parser;

import antlr.collections.AST;
import org.exist.util.Configuration;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XQueryContext;
import org.junit.Test;

import java.io.StringReader;
import java.util.Locale;

/**
 * Microbenchmark for the ANTLR 2 XQuery parser. Times the lexer+parser
 * (AST construction) and the tree walker (semantic build) separately on
 * a set of representative queries.
 *
 * Run with: mvn -pl exist-core test
 *   -Dtest=ParserBenchmark#runBenchmark
 *   -Dexist.parserbench.iterations=20000
 *
 * The test is @Ignore'd by default so it doesn't run in CI.
 */
public class ParserBenchmark {

    private static final int DEFAULT_ITERATIONS = 20_000;
    private static final int WARMUP_ITERATIONS = 2_000;

    private static final Sample[] SAMPLES = {
        new Sample("simple-path",
            "//book[@id = '123']/title/text()"),

        new Sample("xpath-predicates",
            "/library/section[@type='fiction']" +
            "/book[author/last = 'Smith' and year >= 2000]" +
            "/title[1]/text()"),

        new Sample("flwor-medium", """
            xquery version "3.1";
            for $b in //book
            let $author := $b/author
            where $b/year >= 2000
            order by $b/title
            return <result>{ $author/text(), $b/title/text() }</result>"""),

        new Sample("flwor-grouping", """
            xquery version "3.1";
            for $b in //book
            let $cat := $b/@category
            group by $cat
            order by $cat
            return <group cat="{$cat}">{ count($b) }</group>"""),

        new Sample("user-function", """
            xquery version "3.1";
            declare function local:fact($n as xs:integer) as xs:integer {
              if ($n <= 1) then 1 else $n * local:fact($n - 1)
            };
            local:fact(20)"""),

        new Sample("typeswitch", """
            xquery version "3.1";
            declare function local:fmt($v) {
              typeswitch ($v)
                case xs:integer return concat('int=', $v)
                case xs:string return concat('str=', $v)
                case element() return concat('elem=', local-name($v))
                default return 'unknown'
            };
            for $x in (1, 'a', <p/>) return local:fmt($x)"""),

        new Sample("module-import", """
            xquery version "3.1";
            import module namespace fn = "http://www.w3.org/2005/xpath-functions";
            import module namespace map = "http://www.w3.org/2005/xpath-functions/map";
            import module namespace array = "http://www.w3.org/2005/xpath-functions/array";
            let $m := map { 'a': 1, 'b': 2, 'c': 3 }
            let $a := [ 1, 2, 3, 4, 5 ]
            for $k in map:keys($m)
            return map:get($m, $k)"""),

        new Sample("element-constructor", """
            xquery version "3.1";
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>{ /book/title/string() }</title></head>
              <body>
              { for $c in /book/chapter
                return <section id="{ $c/@id }">
                         <h2>{ $c/title/string() }</h2>
                         { for $p in $c//para return <p>{ $p/text() }</p> }
                       </section> }
              </body>
            </html>"""),

        // Realistic application code with camelCase identifiers, underscored
        // names, and digits -- the case where the shape filter short-circuits
        // the keyword-table lookup.
        new Sample("app-camelcase", """
            xquery version "3.1";
            declare function local:renderArticle($articleNode as element()) as element() {
              let $articleId := $articleNode/@xmlId
              let $authorList := $articleNode/teiHeader/fileDesc/titleStmt/author
              let $publishDate := $articleNode/teiHeader/fileDesc/publicationStmt/date/@when
              let $bodyChunks := $articleNode/text/body/div
              return <htmlArticle data_id="{$articleId}">
                <htmlByline>{ string-join($authorList/persName/string(), ', ') }</htmlByline>
                { for $bodyChunk at $chunkIndex in $bodyChunks
                  let $chunkId := concat('chunk_', $chunkIndex)
                  let $headingNode := $bodyChunk/head[1]
                  return <htmlSection data_chunk="{$chunkId}" data_kind="{$bodyChunk/@type}">
                    <htmlHeading>{ $headingNode/string() }</htmlHeading>
                    { for $paragraphNode in $bodyChunk/p return
                        <htmlParagraph data_n="{$paragraphNode/@n}">{ $paragraphNode/string() }</htmlParagraph> }
                  </htmlSection> }
              </htmlArticle>
            };
            local:renderArticle(<doc/>)
            """)
    };

    private static volatile Configuration sharedConfig;

    private static final class Sample {
        final String name;
        final String query;
        Sample(final String name, final String query) {
            this.name = name;
            this.query = query;
        }
    }

    private static AST parseOnly(final String query) throws Exception {
        final XQueryLexer lexer = new XQueryLexer(null, new StringReader(query));
        final XQueryParser parser = new XQueryParser(lexer);
        parser.xpath();
        if (parser.foundErrors()) {
            throw new RuntimeException("parse error: " + parser.getErrorMessage());
        }
        return parser.getAST();
    }

    private static Configuration sharedConfig() {
        Configuration c = sharedConfig;
        if (c == null) {
            synchronized (ParserBenchmark.class) {
                c = sharedConfig;
                if (c == null) {
                    try {
                        c = new Configuration();
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                    sharedConfig = c;
                }
            }
        }
        return c;
    }

    private static void treeWalk(final AST ast) throws Exception {
        final XQueryContext context = new XQueryContext(null, sharedConfig(), null);
        final PathExpr expr = new PathExpr(context);
        final XQueryTreeParser treeParser = new XQueryTreeParser(context);
        treeParser.xpath(ast, expr);
        if (treeParser.foundErrors()) {
            throw new RuntimeException("tree-walk error: " + treeParser.getErrorMessage());
        }
    }

    private static long time(final Runnable r, final int iters) {
        final long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            r.run();
        }
        return System.nanoTime() - start;
    }

    /**
     * Smoke-check: every sample must parse and tree-walk cleanly.
     * Run with -Dtest=ParserBenchmark#smoke
     */
    @Test
    public void smoke() throws Exception {
        for (final Sample s : SAMPLES) {
            final AST ast = parseOnly(s.query);
            treeWalk(ast);
        }
    }

    /**
     * Print per-sample parse and tree-walk timings.
     * Run with -Dtest=ParserBenchmark#runBenchmark
     */
    @Test
    public void runBenchmark() throws Exception {
        // Skip unless explicitly requested via -Dexist.parserbench.run=true
        if (!Boolean.getBoolean("exist.parserbench.run")) {
            return;
        }
        runBenchmarkImpl();
    }

    public static void main(final String[] args) throws Exception {
        runBenchmarkImpl();
    }

    private static void runBenchmarkImpl() throws Exception {
        final int iters = Integer.getInteger(
            "exist.parserbench.iterations", DEFAULT_ITERATIONS).intValue();
        final int warmup = Integer.getInteger(
            "exist.parserbench.warmup", WARMUP_ITERATIONS).intValue();

        System.out.println("=== ParserBenchmark ===");
        System.out.printf(Locale.ROOT,
            "warmup=%d  iterations=%d  java=%s%n",
            warmup, iters, System.getProperty("java.version"));
        System.out.println();
        System.out.printf(Locale.ROOT,
            "%-22s  %12s  %12s  %12s  %12s  %5s%n",
            "sample", "parse us/op", "tree us/op", "total us/op", "throughput/s", "len");
        System.out.println("------------------------------------------------------------------------------------------");

        for (final Sample s : SAMPLES) {
            // warmup parse
            for (int i = 0; i < warmup; i++) {
                parseOnly(s.query);
            }
            // measure parse
            final long parseNanos = time(() -> {
                try {
                    parseOnly(s.query);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }, iters);

            // pre-build one AST for tree-walk timing
            final AST astTemplate = parseOnly(s.query);

            // warmup tree-walk
            for (int i = 0; i < warmup; i++) {
                final AST ast = parseOnly(s.query);
                treeWalk(ast);
            }
            // measure tree-walk only by subtracting parse time from total
            final long totalNanos = time(() -> {
                try {
                    final AST ast = parseOnly(s.query);
                    treeWalk(ast);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }, iters);

            final double parseUs = parseNanos / 1000.0 / iters;
            final double totalUs = totalNanos / 1000.0 / iters;
            final double treeUs = totalUs - parseUs;
            final double thrPs = 1_000_000.0 / totalUs;
            System.out.printf(Locale.ROOT,
                "%-22s  %12.3f  %12.3f  %12.3f  %12.0f  %5d%n",
                s.name, parseUs, treeUs, totalUs, thrPs, s.query.length());
        }
        System.out.println();
    }
}
