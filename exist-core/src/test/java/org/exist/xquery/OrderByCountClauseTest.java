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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * Integration tests for FLWOR {@code order by} and {@code count} clause semantic compliance.
 *
 * <p>Verifies that downstream clauses ({@code count}, {@code where}, {@code let}) that follow
 * an {@code order by} clause see tuples in the sorted order, as required by
 * W3C XQuery 3.1 §3.9.4. Also tests {@code count} after tumbling/sliding window clauses
 * (XQTS TumblingWindowExpr537/538/544 and SlidingWindowExpr538/544).</p>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/5089">Issue #5089</a>
 */
public class OrderByCountClauseTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    /**
     * XQTS count-009: {@code count} clause after {@code order by} must assign rank numbers
     * based on the sorted tuple stream, not the original iteration order.
     */
    @Test
    public void countAfterOrderBy() throws XMLDBException {
        // Query equivalent to XQTS prod-CountClause/count-009, results encoded as "x/y/remainder/rank"
        final String query =
                "string-join((" +
                "  for $x in 1 to 4" +
                "  for $y in 1 to 4" +
                "  count $index" +
                "  let $remainder := $index mod 3" +
                "  order by $remainder, $index" +
                "  count $index2" +
                "  return concat($x, '/', $y, '/', $remainder, '/', $index2)" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals(
                "1/3/0/1 2/2/0/2 3/1/0/3 3/4/0/4 4/3/0/5 " +
                "1/1/1/6 1/4/1/7 2/3/1/8 3/2/1/9 4/1/1/10 4/4/1/11 " +
                "1/2/2/12 2/1/2/13 2/4/2/14 3/3/2/15 4/2/2/16",
                (String) result.getResource(0).getContent());
    }

    /**
     * {@code count} after a descending {@code order by} must still produce ranks 1, 2, 3, …
     * (not N, N-1, … in reverse).
     */
    @Test
    public void countAfterDescendingOrderBy() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for $x in (3, 1, 2)" +
                "  order by $x descending" +
                "  count $rank" +
                "  return concat($x, ':', $rank)" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("3:1 2:2 1:3", (String) result.getResource(0).getContent());
    }

    /**
     * {@code where} clause after {@code order by} must filter the sorted tuple stream.
     * Ranks assigned by a preceding {@code count} must reflect sorted position.
     */
    @Test
    public void whereAfterOrderBy() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for $x in (5, 3, 1, 4, 2)" +
                "  order by $x" +
                "  count $rank" +
                "  where $rank le 3" +
                "  return concat($x, ':', $rank)" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("1:1 2:2 3:3", (String) result.getResource(0).getContent());
    }

    /**
     * {@code let} binding after {@code order by} must see the variable values for each tuple
     * in sorted order.
     */
    @Test
    public void letAfterOrderBy() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for $x in (3, 1, 2)" +
                "  order by $x" +
                "  let $doubled := $x * 2" +
                "  return string($doubled)" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("2 4 6", (String) result.getResource(0).getContent());
    }

    /**
     * Regression: {@code count} must be reset to 1 on each execution of an inner FLWOR
     * expression (XQTS count-010).
     */
    @Test
    public void countResetBetweenOuterIterations() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for $x in 1 to 4 return" +
                "    for $y in 1 to 4" +
                "    count $index" +
                "    return $index" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("1 2 3 4 1 2 3 4 1 2 3 4 1 2 3 4",
                (String) result.getResource(0).getContent());
    }

    /**
     * XQTS TumblingWindowExpr537: {@code count} clause after a tumbling window clause must
     * number windows starting from 1.
     */
    @Test
    public void countAfterTumblingWindow() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for tumbling window $w in (1 to 10)" +
                "  start $s when true()" +
                "  end $e when $e - $s eq 2" +
                "  count $r" +
                "  return concat('w', $r, ':(', string-join($w ! string(.), ','), ')')" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("w1:(1,2,3) w2:(4,5,6) w3:(7,8,9) w4:(10)",
                (String) result.getResource(0).getContent());
    }

    /**
     * XQTS TumblingWindowExpr538: {@code count} and {@code where} after a tumbling window
     * clause — the where clause must use the rank assigned by count.
     */
    @Test
    public void countAndWhereAfterTumblingWindow() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for tumbling window $w in (1 to 10)" +
                "  start $s when true()" +
                "  end $e when $e - $s eq 2" +
                "  count $r" +
                "  where $r le 2" +
                "  return concat('w', $r, ':(', string-join($w ! string(.), ','), ')')" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("w1:(1,2,3) w2:(4,5,6)", (String) result.getResource(0).getContent());
    }

    /**
     * XQTS SlidingWindowExpr538: {@code count} and {@code where} after a sliding window
     * clause — the where clause must use the rank assigned by count.
     */
    @Test
    public void countAndWhereAfterSlidingWindow() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for sliding window $w in (1 to 10)" +
                "  start $s when true()" +
                "  end $e when $e - $s eq 2" +
                "  count $r" +
                "  where $r le 2" +
                "  return concat('w', $r, ':(', string-join($w ! string(.), ','), ')')" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("w1:(1,2,3) w2:(2,3,4)", (String) result.getResource(0).getContent());
    }

    /**
     * XQTS SlidingWindowExpr540: {@code order by} directly after a sliding window clause,
     * where the return expression references the window variable.
     */
    @Test
    public void orderByAfterSlidingWindow() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for sliding window $w in (1 to 10)" +
                "  start $s when true()" +
                "  only end $e when $e - $s eq 2" +
                "  order by $w[2] descending" +
                "  return string-join($w ! string(.), ' ')" +
                "), '|')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        // Windows sorted by $w[2] descending: 8,9,10 (w[2]=9) ... 1,2,3 (w[2]=2)
        assertEquals("8 9 10|7 8 9|6 7 8|5 6 7|4 5 6|3 4 5|2 3 4|1 2 3",
                (String) result.getResource(0).getContent());
    }

    /**
     * XQTS TumblingWindowExpr540: {@code order by} directly after a tumbling window clause,
     * where the return expression references the window variable.
     */
    @Test
    public void orderByAfterTumblingWindow() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for tumbling window $w in (1 to 10)" +
                "  start $s when true()" +
                "  only end $e when $e - $s eq 2" +
                "  order by $w[2] descending" +
                "  return string-join($w ! string(.), ' ')" +
                "), '|')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        // Windows: (1,2,3), (4,5,6), (7,8,9). Sorted by $w[2] desc: 9>6>3 → (7,8,9), (4,5,6), (1,2,3)
        assertEquals("7 8 9|4 5 6|1 2 3",
                (String) result.getResource(0).getContent());
    }

    /**
     * XQTS TumblingWindowExpr544: {@code count} after a tumbling window, with a nested
     * {@code order by} inside the return expression — the outer count must be unaffected.
     */
    @Test
    public void countAfterTumblingWindowWithNestedOrderBy() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for tumbling window $w in (1 to 10)" +
                "  start $s when true()" +
                "  only end $e when $e - $s eq 2" +
                "  count $r" +
                "  return concat('w', $r, ':(', string-join(" +
                "    for $i in $w order by $i descending return string($i)" +
                "  , ','), ')')" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("w1:(3,2,1) w2:(6,5,4) w3:(9,8,7)",
                (String) result.getResource(0).getContent());
    }

    /**
     * XQTS orderBy66: two {@code order by} clauses in the same FLWOR, with {@code count}
     * and {@code let} between them, and {@code where} after the second {@code order by}.
     */
    @Test
    public void twoOrderByClausesWithCountLetWhere() throws XMLDBException {
        // for $i in 1 to 100
        // order by -$i                   → sorted: 100, 99, ..., 1
        // count $count                   → $count = 1..100
        // let $e := <e i="{$i}" pos="{$count}"/>
        // order by number($e/@i)         → sorted: $i=1..100
        // where $count gt 90             → keeps $i=1..10 (which had $count=100..91)
        // return $e!@pos!number()        → 100, 99, 98, ..., 91
        final String query =
                "string-join((" +
                "  for $i in 1 to 100" +
                "  order by -$i" +
                "  count $count" +
                "  let $e := <e i=\"{$i}\" pos=\"{$count}\"/>" +
                "  order by number($e/@i)" +
                "  where $count gt 90" +
                "  return string($e/@pos)" +
                "), ',')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals("100,99,98,97,96,95,94,93,92,91",
                (String) result.getResource(0).getContent());
    }

    /**
     * XQTS SlidingWindowExpr544: {@code count} after a sliding window, with a nested
     * {@code order by} inside the return expression — the outer count must be unaffected.
     */
    @Test
    public void countAfterSlidingWindowWithNestedOrderBy() throws XMLDBException {
        final String query =
                "string-join((" +
                "  for sliding window $w in (1 to 10)" +
                "  start $s when true()" +
                "  only end $e when $e - $s eq 2" +
                "  count $r" +
                "  return concat('w', $r, ':(', string-join(" +
                "    for $i in $w order by $i descending return string($i)" +
                "  , ','), ')')" +
                "), ' ')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        assertEquals(
                "w1:(3,2,1) w2:(4,3,2) w3:(5,4,3) w4:(6,5,4) " +
                "w5:(7,6,5) w6:(8,7,6) w7:(9,8,7) w8:(10,9,8)",
                (String) result.getResource(0).getContent());
    }
}
