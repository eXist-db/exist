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
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * A function argument with the wrong cardinality is a type error, XPTY0004.
 *
 * <p>{@link DynamicCardinalityCheck} raised it with no error code at all, so the code only appeared
 * inside the message text and the error surfaced as {@code exerr:ERROR}. A {@code catch
 * err:XPTY0004} clause could not catch it, and 30 XQTS tests that assert the code failed for that
 * reason alone. These tests assert the code the way a query would: by catching it.</p>
 */
public class DynamicCardinalityCheckErrorCodeTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private String codeRaisedBy(final String expression) throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query("try { " + expression + " } catch * { string($err:code) }");
        return rs.getResource(0).getContent().toString();
    }

    /** XQTS K-MatchesFunc-1. */
    @Test
    public void emptyPatternForMatches() throws XMLDBException {
        assertEquals("err:XPTY0004", codeRaisedBy("matches('input', ())"));
    }

    /** XQTS K-MatchesFunc-3. */
    @Test
    public void emptyFlagsForMatches() throws XMLDBException {
        assertEquals("err:XPTY0004", codeRaisedBy("matches('input', 'pattern', ())"));
    }

    /** XQTS fo-test-fn-string-004: too many items where one is required. */
    @Test
    public void tooManyItemsForString() throws XMLDBException {
        assertEquals("err:XPTY0004", codeRaisedBy("string(('a', 'b'))"));
    }

    /** The specific catch clause the code is for must now work. */
    @Test
    public void isCatchableBySpecificClause() throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query("try { matches('input', ()) } catch err:XPTY0004 { 'caught' }");
        assertEquals("caught", rs.getResource(0).getContent().toString());
    }
}
