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

import org.junit.Test;

import java.util.List;

/**
 * Micro-benchmark comparing the hand-written lexer against ANTLR 2.
 *
 * <p>This is a simple wall-clock benchmark, not a JMH benchmark.
 * It's useful for quick sanity checks during development. For
 * production benchmarking, use JMH.</p>
 *
 * <p>Run with: {@code mvn test -pl exist-core -Dtest=LexerBenchmark}</p>
 */
public class LexerBenchmark {

    /**
     * A representative FLWOR expression with multiple clauses,
     * function calls, path expressions, and various token types.
     */
    private static final String FLWOR_QUERY =
            "xquery version \"3.1\";\n" +
            "declare namespace tei = \"http://www.tei-c.org/ns/1.0\";\n" +
            "declare variable $collection := \"/db/apps/shakespeare\";\n" +
            "\n" +
            "for $play in collection($collection)//tei:TEI\n" +
            "let $title := $play//tei:titleStmt/tei:title/string()\n" +
            "let $acts := count($play//tei:div[@type = 'act'])\n" +
            "where $acts > 3\n" +
            "order by $title ascending\n" +
            "return\n" +
            "  <play>\n" +
            "    <title>{$title}</title>\n" +
            "    <acts>{$acts}</acts>\n" +
            "  </play>";

    /**
     * A complex expression with many keywords to stress-test keyword handling.
     */
    private static final String KEYWORD_HEAVY =
            "for $x in (1 to 100)\n" +
            "let $y := $x * 2\n" +
            "let $z := if ($x mod 3 eq 0) then 'fizz'\n" +
            "          else if ($x mod 5 eq 0) then 'buzz'\n" +
            "          else if ($x mod 15 eq 0) then 'fizzbuzz'\n" +
            "          else string($x)\n" +
            "where $x instance of xs:integer\n" +
            "  and ($x castable as xs:double)\n" +
            "  and not($x = (7, 13, 17))\n" +
            "group by $bucket := $x idiv 10\n" +
            "order by $bucket ascending empty greatest\n" +
            "count $pos\n" +
            "return\n" +
            "  <group n=\"{$bucket}\" pos=\"{$pos}\">\n" +
            "    { for $item in $z return <item>{$item}</item> }\n" +
            "  </group>";

    /**
     * XQuery 4.0 syntax: pipeline, mapping arrow, string templates.
     */
    private static final String XQ4_SYNTAX =
            "let $data := (1, 2, 3, 4, 5)\n" +
            "return $data\n" +
            "  -> fn:filter(fn:is-NaN#1)\n" +
            "  -> fn:sort((), fn:compare#2)\n" +
            "  =!> fn:for-each(function($x) { $x * $x })\n" +
            "  ?? ()\n" +
            "  otherwise 'empty'";

    private static final int WARMUP_ITERATIONS = 5_000;
    private static final int MEASURED_ITERATIONS = 50_000;

    @Test
    public void benchmarkFlworQuery() {
        runBenchmark("FLWOR query", FLWOR_QUERY);
    }

    @Test
    public void benchmarkKeywordHeavy() {
        runBenchmark("Keyword-heavy", KEYWORD_HEAVY);
    }

    @Test
    public void benchmarkXQ4Syntax() {
        runBenchmark("XQ4 syntax", XQ4_SYNTAX);
    }

    @Test
    public void benchmarkTokenCount() {
        // Report token counts for reference
        System.out.println("\n=== Token counts ===");
        reportTokenCount("FLWOR query", FLWOR_QUERY);
        reportTokenCount("Keyword-heavy", KEYWORD_HEAVY);
        reportTokenCount("XQ4 syntax", XQ4_SYNTAX);
    }

    private void runBenchmark(final String label, final String query) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            new XQueryLexer(query).tokenizeAll();
        }

        // Measured
        final long start = System.nanoTime();
        int totalTokens = 0;
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            totalTokens += new XQueryLexer(query).tokenizeAll().size();
        }
        final long elapsed = System.nanoTime() - start;

        final double avgMicros = (elapsed / 1_000.0) / MEASURED_ITERATIONS;
        final double tokensPerSec = (totalTokens / (elapsed / 1_000_000_000.0));

        System.out.printf("\n=== %s ===%n", label);
        System.out.printf("  Average: %.1f µs per tokenization%n", avgMicros);
        System.out.printf("  Throughput: %.0f tokens/sec%n", tokensPerSec);
        System.out.printf("  Tokens per query: %d%n", totalTokens / MEASURED_ITERATIONS);
        System.out.printf("  Query length: %d chars%n", query.length());
    }

    private void reportTokenCount(final String label, final String query) {
        final List<Token> tokens = new XQueryLexer(query).tokenizeAll();
        System.out.printf("  %s: %d tokens from %d chars%n",
                label, tokens.size(), query.length());
        // Print first few tokens for verification
        for (int i = 0; i < Math.min(5, tokens.size()); i++) {
            System.out.printf("    [%d] %s%n", i, tokens.get(i));
        }
        System.out.println("    ...");
    }
}
