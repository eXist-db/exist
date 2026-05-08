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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Correctness and performance regression coverage for {@link FunIndexOf}'s
 * positional-index cache (issue
 * <a href="https://github.com/eXist-db/exist/issues/3682">#3682</a>).
 *
 * <p>Without the cache, repeated invocations of {@code fn:index-of} against
 * the same source sequence inside a FLWOR or predicate are O(N) each, giving
 * O(N^2) overall and the multi-minute runtimes reported in #3682. The cache
 * collapses repeated lookups to O(log N) once the source is recognised on
 * its second hit. These tests pin both the behaviour and a coarse runtime
 * upper bound so a regression in either direction will fail explicitly.
 */
public class FunIndexOfPerformanceTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    private String executeOne(final String query) throws XMLDBException {
        final ResourceSet rs = server.executeQuery(query);
        assertEquals(1, rs.getSize());
        return rs.getResource(0).getContent().toString();
    }

    @Test
    public void smallSequenceStrings() throws XMLDBException {
        assertEquals("1 4", executeOne(
                "string-join(fn:index-of((\"a\", \"sport\", \"and\", \"a\", \"pastime\"), \"a\"), ' ')"));
    }

    @Test
    public void smallSequenceIntegers() throws XMLDBException {
        assertEquals("2 4",
                executeOne("string-join(fn:index-of((15, 40, 25, 40, 10), 40), ' ')"));
    }

    @Test
    public void emptySource() throws XMLDBException {
        assertEquals("0", executeOne("count(fn:index-of((), 1))"));
    }

    @Test
    public void noMatch() throws XMLDBException {
        assertEquals("0", executeOne("count(fn:index-of((1, 2, 3), 99))"));
    }

    /**
     * Repeated lookups against the same let-bound sequence — the pattern that
     * exercised the cache. Verifies the cached path returns the same answers
     * as the linear scan would.
     */
    @Test
    public void repeatedLookupsAgainstStableSource() throws XMLDBException {
        final String query =
                "let $seq := (for $i in 1 to 100 return $i, 1, 50, 100) "
                        + "return string-join("
                        + "  for $v in (1, 50, 100, 7, 999) "
                        + "  return string-join(fn:index-of($seq, $v), ',')"
                        + ", '|')";
        assertEquals("1,101|50,102|100,103|7|", executeOne(query));
    }

    /** Cross-numeric-type equality must still match (xs:integer vs xs:double). */
    @Test
    public void crossNumericTypeEquality() throws XMLDBException {
        // Build a longer sequence so the threshold is exceeded and the cache
        // kicks in; the trailing search term is xs:double, the sequence
        // contains xs:integer values.
        final String query =
                "let $seq := (1 to 50) "
                        + "return string-join(fn:index-of($seq, xs:double(7.0)), ',')";
        assertEquals("7", executeOne(query));
    }

    /** NaN never matches itself under eq, even at scale. */
    @Test
    public void nanNeverMatches() throws XMLDBException {
        final String query =
                "let $seq := (for $i in 1 to 50 return xs:double($i), xs:double('NaN')) "
                        + "return count(fn:index-of($seq, xs:double('NaN')))";
        assertEquals("0", executeOne(query));
    }

    /** Source containing duplicates must report all positions in ascending order. */
    @Test
    public void duplicatesReportAllPositions() throws XMLDBException {
        final String query =
                "let $seq := (for $i in 1 to 50 return ($i, $i)) "
                        + "return string-join(fn:index-of($seq, 7), ',')";
        // Item 7 is at positions (2*7 - 1, 2*7) = (13, 14)
        assertEquals("13,14", executeOne(query));
    }

    /** Issue #3682 reproducer (variant 1 — FLWOR distinct-values + index-of). */
    @Test
    public void issue3682FlworVariantCompletesQuickly() throws XMLDBException {
        final int n = 5000;
        final String query =
                "let $seq := (1 to " + n + ", 1) "
                        + "let $non-distinct-values := for $v in distinct-values($seq) return $v[count(index-of($seq,$v)) > 1] "
                        + "return string-join($non-distinct-values, ',')";
        final long t0 = System.nanoTime();
        final String result = executeOne(query);
        final long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals("1", result);
        // Pre-fix runtime on this scale was ~1.5 seconds. The cached path
        // brings it under ~2 seconds with a generous CI margin; without the
        // fix the test would take 10 seconds or more on a slow runner.
        assertTrue("Expected #3682 query to complete under 10s, took " + elapsedMs + "ms",
                elapsedMs < 10_000);
    }

    /**
     * Length-preserving mutation across calls — the source sequence differs
     * from the previous call at exactly one position, sized so it exceeds the
     * cache threshold. Without the post-lookup verification (PR #6315
     * follow-up) the fingerprint matched on the unchanged sample positions
     * and returned stale cached indexes; this test pins correctness against
     * that regression.
     */
    @Test
    public void lengthPreservingMutationReturnsCorrectPositions() throws XMLDBException {
        final String query = """
                let $seq := (1 to 1000)
                return string-join(
                  for $i in 1 to 50
                  let $modified := for $j in 1 to 1000 return if ($j = $i) then 999 else $seq[$j]
                  return string-join(index-of($modified, 999), ','),
                  '|')""";
        // For each iteration, 999 occurs at position $i (the mutation) and at
        // position 999 (unchanged $seq[999] = 999).
        final StringBuilder expected = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            if (i > 1) {
                expected.append('|');
            }
            expected.append(i).append(",999");
        }
        assertEquals(expected.toString(), executeOne(query));
    }

    /** Issue #3682 variant 3 (predicate filter using count(index-of)). */
    @Test
    public void issue3682PredicateVariantCompletesQuickly() throws XMLDBException {
        final int n = 5000;
        final String query =
                "let $seq := (1 to " + n + ", 1) "
                        + "return string-join(distinct-values($seq[count(index-of($seq, .)) gt 1]), ',')";
        final long t0 = System.nanoTime();
        final String result = executeOne(query);
        final long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals("1", result);
        assertTrue("Expected #3682 predicate variant to complete under 10s, took " + elapsedMs + "ms",
                elapsedMs < 10_000);
    }
}
