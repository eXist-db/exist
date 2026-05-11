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

/**
 * Tests for XQuery 3.1 section 3.9.1: namespace declaration attributes on a
 * direct element constructor must be added to the statically known
 * namespaces of the constructor's content and attribute values, so prefixed
 * QNames inside enclosed expressions resolve correctly.
 *
 * Covers prod-DirElemContent.namespace clusters A (prefixed names in
 * enclosed exprs) and B (default-element namespace in enclosed exprs).
 */
public class DirectElementConstructorInScopeNamespaceTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    // Cluster A - prefixed namespace declaration on the element should be
    // visible to enclosed expressions in attribute values.

    @Test
    public void prefixedCastInsideAttributeValue() throws Exception {
        final String result = executeStringValue(
                "<e a=\"{1 cast as p:string}\" xmlns:p=\"http://www.w3.org/2001/XMLSchema\"/>/@a/string()");
        assertEquals("1", result);
    }

    @Test
    public void prefixedTreatInsideAttributeValue() throws Exception {
        final String result = executeStringValue(
                "<e a=\"{1 treat as p:integer}\" xmlns:p=\"http://www.w3.org/2001/XMLSchema\"/>/@a/string()");
        assertEquals("1", result);
    }

    @Test
    public void prefixedInstanceOfInsideAttributeValue() throws Exception {
        final String result = executeStringValue(
                "<e a=\"{1 instance of p:integer}\" xmlns:p=\"http://www.w3.org/2001/XMLSchema\"/>/@a/string()");
        assertEquals("true", result);
    }

    @Test
    public void prefixedCastableInsideAttributeValue() throws Exception {
        final String result = executeStringValue(
                "<e a=\"{1 castable as p:integer}\" xmlns:p=\"http://www.w3.org/2001/XMLSchema\"/>/@a/string()");
        assertEquals("true", result);
    }

    @Test
    public void prefixedVariableNameInsideAttributeValue() throws Exception {
        final String result = executeStringValue(
                "<a attr=\"{let $p:name := 3 return $p:name}\" xmlns:p=\"urn:foo\"/>/@attr/string()");
        assertEquals("3", result);
    }

    // Cluster B - default element namespace declaration should make
    // unprefixed type references inside enclosed exprs resolve.

    @Test
    public void defaultNamespaceInstanceOfInsideAttributeValue() throws Exception {
        final String result = executeStringValue(
                "<e a=\"{1 instance of integer}\" xmlns=\"http://www.w3.org/2001/XMLSchema\"/>/@a/string()");
        assertEquals("true", result);
    }

    // State-leakage guard: same prefix bound to different URIs in two
    // sequential constructors must not bleed across.

    @Test
    public void prefixRedeclarationDoesNotLeak() throws Exception {
        final String result = executeStringValue(
                "let $a := <a xmlns:p=\"http://www.w3.org/2001/XMLSchema\">{1 instance of p:integer}</a>" +
                        " let $b := <b xmlns:p=\"urn:other\">marker</b>" +
                        " return concat($a/string(), '|', namespace-uri-for-prefix('p', $b))");
        assertEquals("true|urn:other", result);
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
