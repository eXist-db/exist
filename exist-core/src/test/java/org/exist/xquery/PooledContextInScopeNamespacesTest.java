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
import org.exist.storage.XQueryPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A compiled query returned to the {@link XQueryPool} must not carry the in-scope namespaces of its
 * last execution into the next one.
 *
 * <p>Attribute namespace fixup during element construction consults the context's in-scope
 * namespace maps. Declarations made outside an element constructor -- by the fixup itself, or by a
 * caller configuring the context -- land in the top-level maps, and {@link XQueryContext#reset()}
 * did not clear them. On a pooled context they therefore survived into whichever execution next
 * borrowed it. The XQTS runner reuses one cached query to serialize every assert-xml result, so
 * the bindings one test left behind decided how the next test's attributes were prefixed: the
 * Constr-inscope-1 to -4 tests, which use the prefixes foo and XXX with swapped URIs, flipped
 * nondeterministically depending on which of them had last used the pooled instance (#6704).</p>
 */
public class PooledContextInScopeNamespacesTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String PARENT1 = "http://www.example.com/parent1";
    private static final String PARENT2 = "http://www.example.com/parent2";

    /** XQTS Constr-inscope-3: foo is rebound on the new element, so attr1 must get a fresh prefix. */
    private static final String QUERY =
        "serialize(for $x in <parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/> "
      + "return <new xmlns:foo=\"http://www.example.com\">{$x//@*:attr1}</new>)";

    /** The runner's sequence: borrow or compile, configure, execute, clean up, return to the pool. */
    private static String run(final Consumer<XQueryContext> configure) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final Source source = new StringSource(QUERY);
            final XQueryPool xqueryPool = pool.getXQueryPool();
            final XQuery xquery = pool.getXQueryService();
            CompiledXQuery compiled = xqueryPool.borrowCompiledXQuery(broker, source);
            final XQueryContext context;
            if (compiled == null) {
                context = new XQueryContext(pool);
                configure.accept(context);
                compiled = xquery.compile(context, source);
            } else {
                context = compiled.getContext();
                configure.accept(context);
            }
            try {
                final Sequence result = xquery.execute(broker, compiled, null);
                return result.getStringValue();
            } finally {
                context.runCleanupTasks();
                xqueryPool.returnCompiledXQuery(source, compiled);
            }
        }
    }

    private static Element parse(final String xml) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    @Test
    public void bindingsFromAPreviousExecutionDoNotChangeTheNextOne() throws Exception {
        // What serializing a Constr-inscope-2/-4 result leaves behind: the same two prefixes,
        // bound to the other URIs.
        run(context -> {
            context.declareInScopeNamespace("foo", PARENT1);
            context.declareInScopeNamespace("XXX", PARENT2);
        });

        final String serialized = run(context -> { });
        final Element element = parse(serialized);
        assertNotNull("attr1 must stay in " + PARENT1 + "; got " + serialized,
                element.getAttributeNodeNS(PARENT1, "attr1"));
    }

    /**
     * Compilation also resets the context: when the optimizer rewrites the tree, the query is reset
     * and analyzed again. Namespaces a caller declared before compiling must survive that, or a path
     * with an optimizable predicate over a caller-declared prefix fails with XPST0081 -- which is how
     * the XQTS runner supplies an environment's prefixes to its assertions (json-to-xml-008, -009).
     */
    @Test
    public void callerDeclaredNamespacesSurviveReanalysisDuringCompilation() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext context = new XQueryContext(pool);
            context.declareInScopeNamespace("j", "http://www.w3.org/2005/xpath-functions");
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(context,
                    new StringSource("count(json-to-xml('{\"a\":{\"b\":1}}')/j:map/j:map[@key = 'a'])"));
            try {
                assertEquals("1", xquery.execute(broker, compiled, null).getStringValue());
            } finally {
                context.runCleanupTasks();
            }
        }
    }

    @Test
    public void resetLeavesNoInScopeNamespacesBehind() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final XQueryContext context = new XQueryContext(pool);
        context.declareInScopeNamespace("foo", PARENT1);
        context.reset();
        assertTrue("in-scope namespaces after reset: " + context.getInScopeNamespaces(),
                context.getInScopeNamespaces().isEmpty());
        assertTrue("in-scope prefixes after reset: " + context.getInScopePrefixes(),
                context.getInScopePrefixes().isEmpty());
    }
}
