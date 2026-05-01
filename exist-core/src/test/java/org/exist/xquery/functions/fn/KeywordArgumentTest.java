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
 * Tests XQuery 4.0 keyword argument syntax (name := value).
 *
 * Verifies that keyword args work with both NCNAME parameter names and
 * names that are reserved keywords (e.g. value, to, node, function).
 */
public class KeywordArgumentTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void mathLog10WithValueKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "math:log10(value := 100.0)");
        assertEquals(1, rs.getSize());
        assertEquals("2", rs.getResource(0).getContent().toString());
    }

    @Test
    public void mathLog10AsPartialApplicationKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "math:log10(value := ?) instance of function(xs:double?) as xs:double?");
        assertEquals(1, rs.getSize());
        assertEquals("true", rs.getResource(0).getContent().toString());
    }

    @Test
    public void yearFromDateValueKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "fn:year-from-date(value := xs:date('2026-04-29'))");
        assertEquals(1, rs.getSize());
        assertEquals("2026", rs.getResource(0).getContent().toString());
    }

    @Test
    public void hoursFromTimeValueKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "fn:hours-from-time(value := xs:time('15:30:00'))");
        assertEquals(1, rs.getSize());
        assertEquals("15", rs.getResource(0).getContent().toString());
    }

    @Test
    public void reverseInputKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "string-join(fn:reverse(input := (1,2,3)), ',')");
        assertEquals(1, rs.getSize());
        assertEquals("3,2,1", rs.getResource(0).getContent().toString());
    }

    @Test
    public void matchesValueKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "fn:matches(value := 'hello', pattern := '^h')");
        assertEquals(1, rs.getSize());
        assertEquals("true", rs.getResource(0).getContent().toString());
    }

    /**
     * Subsequence-where with `to` keyword arg -- `to` is a reserved keyword,
     * so this exercises the parser change that allows reserved keywords as
     * keyword-argument names.
     */
    @Test
    public void subsequenceWhereWithToKeyword() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "string-join(fn:subsequence-where(1 to 5, to := function($x){$x = 3}), ',')");
        assertEquals(1, rs.getSize());
        assertEquals("1,2,3", rs.getResource(0).getContent().toString());
    }

    /**
     * Verify that `value := expr` still works after we generalised the
     * keyword-arg name to any reserved keyword.
     */
    @Test
    public void valueKeywordStillWorks() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "math:exp(value := 0.0)");
        assertEquals(1, rs.getSize());
        assertEquals("1", rs.getResource(0).getContent().toString());
    }

    /**
     * Make sure `1 to 5` still parses as a range expression in argument
     * position (the parser change must not regress this).
     */
    @Test
    public void rangeExprInArgumentStillParses() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "count((1 to 5))");
        assertEquals(1, rs.getSize());
        assertEquals("5", rs.getResource(0).getContent().toString());
    }

    @Test
    public void floorWithValueKeywordPlaceholder() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "fn:floor(value := ?) instance of function(xs:numeric?) as xs:numeric?");
        assertEquals(1, rs.getSize());
        assertEquals("true", rs.getResource(0).getContent().toString());
    }

    /**
     * XQTS-style shape: `f(name1 := ?, name2 := ?) instance of function(*)`.
     * Two keyword-arg placeholders separated by comma — exercises the
     * per-keyword placeholder path AND the multi-arg keyword resolution.
     */
    @Test
    public void twoKeywordPlaceholdersInstanceOfFunctionStar() throws XMLDBException {
        final ResourceSet rs = existEmbeddedServer.executeQuery(
                "fn:index-where(input := ?, predicate := ?) instance of function(*)");
        assertEquals(1, rs.getSize());
        assertEquals("true", rs.getResource(0).getContent().toString());
    }
}
