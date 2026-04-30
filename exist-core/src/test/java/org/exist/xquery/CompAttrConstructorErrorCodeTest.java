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

import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Targeted tests for the QT4 XQTS prod-CompAttrConstructor failure patterns
 * addressed in v2/xq31-compliance-fixes:
 *  - EnclosedExpr SAXException unwrapping for XQTY0024 / XQDY0025
 *  - QName.parse() handling of EQName whitespace and surrounding whitespace
 */
public class CompAttrConstructorErrorCodeTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void xqty0024AttributeAfterPi() {
        // K2-ComputeConAttr-2: <elem> <?target c ?> {attribute name {"x"}} </elem>
        assertErrorCode("XQTY0024",
                "<elem> <?target c ?> {attribute name {\"x\"}} </elem>");
    }

    @Test
    public void xqdy0025DuplicateComputedAttribute() {
        // Computed attributes with duplicate names should raise XQDY0025 (not generic ERROR).
        assertErrorCode("XQDY0025",
                "<foo>{attribute a {\"1\"}, attribute a {\"2\"}}</foo>");
    }

    @Test
    public void eqnameNamespaceWithWhitespace() throws Exception {
        // Constr-compattr-eqname-2: " Q{ _   _ }x " with surrounding whitespace
        // should yield local-name 'x' and namespace '_ _' (collapsed).
        final String result = executeStringValue(
                "let $r := attribute { \" Q{ _   _ }x \" } {} return " +
                        "concat(local-name($r), '|', namespace-uri($r))");
        assertEquals("x|_ _", result);
    }

    @Test
    public void prefixedQNameWithSurroundingWhitespace() throws Exception {
        // Constr-compattr-eqname-3: " xml:x " should auto-bind xml namespace
        final String result = executeStringValue(
                "let $r := attribute { \" xml:x \" } {} return " +
                        "concat(name($r), '|', namespace-uri($r))");
        assertEquals("xml:x|http://www.w3.org/XML/1998/namespace", result);
    }

    private void assertErrorCode(final String expectedErrorCode, final String query) {
        try {
            final Sequence result = executeXQuery(query);
            fail("Expected error " + expectedErrorCode + " but query returned: " + result.getStringValue());
        } catch (final XPathException e) {
            final String actual = e.getErrorCode() == null ? "<null>" : e.getErrorCode().getErrorQName().getLocalPart();
            assertEquals("Wrong error code (message: " + e.getMessage() + ")",
                    expectedErrorCode, actual);
        } catch (final Exception e) {
            // unwrap nested XPathException
            Throwable cause = e;
            while (cause != null && !(cause instanceof XPathException)) {
                cause = cause.getCause();
            }
            if (cause instanceof XPathException xpe) {
                final String actual = xpe.getErrorCode() == null ? "<null>" : xpe.getErrorCode().getErrorQName().getLocalPart();
                assertEquals("Wrong error code (message: " + xpe.getMessage() + ")",
                        expectedErrorCode, actual);
            } else {
                fail("Unexpected exception type: " + e.getClass().getName() + " - " + e.getMessage());
            }
        }
    }

    private String executeStringValue(final String query) throws Exception {
        final Sequence result = executeXQuery(query);
        return result.getStringValue();
    }

    private Sequence executeXQuery(final String query) throws Exception {
        final Source source = new StringSource(query);
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();

        try (final DBBroker broker = brokerPool.get(
                Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(brokerPool);
            final CompiledXQuery compiled = xquery.compile(context, source);
            return xquery.execute(broker, compiled, null);
        }
    }
}
