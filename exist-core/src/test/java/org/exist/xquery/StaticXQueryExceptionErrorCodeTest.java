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
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Targeted regression tests for the XQTS prod-Literal sub-cluster where
 * eXist's lexer/parser correctly rejected the query but reported the generic
 * {@code ERROR} code instead of the W3C-mandated {@code XPST0003} static
 * syntax error code.
 *
 * <p>Covers the lexer-error paths (unterminated string, invalid entity reference,
 * invalid double literal exponent, invalid hexadecimal character reference)
 * surfaced by tests such as {@code K-Literals-31}, {@code Literals006},
 * {@code Literals051}, {@code K2-Literals-22}, {@code K-Literals-50}.
 */
public class StaticXQueryExceptionErrorCodeTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void unterminatedStringLiteralLiterals006() {
        // "test  -- closing quote missing
        assertStaticError("\"test");
    }

    @Test
    void mismatchedStringDelimitersLiterals008() {
        // 'test"  -- opens with apostrophe, closes with double-quote
        assertStaticError("'test\"");
    }

    @Test
    void invalidDoubleLiteralExponentLiterals051() {
        assertStaticError("1ee2");
    }

    @Test
    void invalidDoubleLiteralUppercaseELiterals052() {
        assertStaticError("1EE2");
    }

    @Test
    void invalidEntityReferenceMissingSemicolonKLiterals31() {
        // "a string &;"  -- empty / invalid entity reference
        assertStaticError("\"a string &;\"");
    }

    @Test
    void invalidDecimalCharRefKLiterals32() {
        // "a string &#;"  -- decimal char ref with no digits
        assertStaticError("\"a string &#;\"");
    }

    @Test
    void invalidHexCharRefKLiterals38() {
        // "a string &#x;"  -- hex char ref with no digits
        assertStaticError("\"a string &#x;\"");
    }

    @Test
    void unknownNamedEntityKLiterals41() {
        // "a string &unknown;"  -- not one of the five predefined entities
        assertStaticError("\"a string &unknown;\"");
    }

    @Test
    void charRefOutsideStringLiteralKLiterals50() {
        // Character references are only allowed inside string literals
        assertStaticError("1 &lt;= 3");
    }

    @Test
    void minusInHexCharRefK2Literals22() {
        assertStaticError("\"&#x-20;\"");
    }

    @Test
    void plusInDecimalCharRefK2Literals25() {
        assertStaticError("\"&#+20;\"");
    }

    @Test
    void trailingQuoteJunkKLiterals24() {
        // 33"  -- trailing unmatched double-quote
        assertStaticError("33\"");
    }

    private void assertStaticError(final String query) {
        try {
            final Source source = new StringSource(query);
            final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
            final XQuery xquery = brokerPool.getXQueryService();
            final XQueryContext context = new XQueryContext(brokerPool);
            xquery.compile(context, source);
            fail("Expected XPST0003 but query compiled successfully: " + query);
        } catch (final XPathException e) {
            final String actual = e.getErrorCode() == null
                    ? "<null>"
                    : e.getErrorCode().getErrorQName().getLocalPart();
            assertEquals("XPST0003",
                    actual, "Wrong error code for query [" + query + "] (message: " + e.getMessage() + ")");
        } catch (final Exception e) {
            fail("Unexpected exception for query [" + query + "]: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }
}
