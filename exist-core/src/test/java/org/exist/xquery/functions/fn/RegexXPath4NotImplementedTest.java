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

import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * fn:matches and fn:analyze-string raise XPST0017 with a "not yet implemented"
 * message when an XPath 4.0 lookaround construct (e.g. (*positive_lookahead:...))
 * appears in the pattern. Previously the XQ4 dispatch branch was silently
 * inactive on the XQ 3.1 runtime, so such patterns fell through to Saxon and
 * produced opaque regex errors.
 */
public class RegexXPath4NotImplementedTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void matchesLookaroundRaisesNotImplemented() {
        assertNotImplemented("fn:matches('foobar', '(*positive_lookahead:foo)bar')");
    }

    @Test
    public void analyzeStringLookaroundRaisesNotImplemented() {
        assertNotImplemented("fn:analyze-string('foobar', '(*positive_lookahead:foo)bar')");
    }

    @Test
    public void matchesPlainPatternStillWorks() throws Exception {
        assertEquals("true", executeStringValue("fn:matches('foobar', 'foo')"));
    }

    private void assertNotImplemented(final String query) {
        try {
            executeStringValue(query);
            fail("Expected XPST0017 for XPath 4.0 lookaround");
        } catch (final Exception e) {
            final XPathException xpe = unwrap(e);
            assertNotNull("Expected an XPathException, got: " + e, xpe);
            assertEquals(ErrorCodes.XPST0017, xpe.getErrorCode());
            assertTrue("Expected 'not yet implemented' message, got: " + xpe.getMessage(),
                    xpe.getMessage().contains("not yet implemented"));
        }
    }

    private static XPathException unwrap(final Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof XPathException xpe) {
                return xpe;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private String executeStringValue(final String query) throws Exception {
        final Source source = new StringSource(query);
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();

        try (final DBBroker broker = brokerPool.get(
                Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(brokerPool);
            final CompiledXQuery compiled = xquery.compile(context, source);
            return xquery.execute(broker, compiled, null).getStringValue();
        }
    }
}
