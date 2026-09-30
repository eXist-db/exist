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
package org.exist.dom.memtree;

import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * An attribute copied into a constructed element keeps its namespace, whatever its prefix means on
 * the new element (XQuery 3.1 &sect;3.9.3.4, copy-namespaces preserve).
 *
 * <p>The assertions parse the serialized result and check the attribute's namespace URI, not its
 * prefix: a prefix that the element binds to a different URI puts the attribute in the wrong
 * namespace, which is the failure that matters and the one XQTS Constr-inscope-1 to -4 detect.</p>
 */
public class AttributeNamespaceFixupTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String EX = "http://www.example.com";
    private static final String PARENT1 = "http://www.example.com/parent1";
    private static final String PARENT2 = "http://www.example.com/parent2";

    private static String run(final String query, final Consumer<XQueryContext> configure) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final Source source = new StringSource(query);
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
                return xquery.execute(broker, compiled, null).getStringValue();
            } finally {
                context.runCleanupTasks();
                xqueryPool.returnCompiledXQuery(source, compiled);
            }
        }
    }

    private static String run(final String query) throws Exception {
        return run(query, context -> { });
    }

    private static Element parse(final String xml) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    private static void assertAttributeIn(final String serialized, final String uri, final String localName) throws Exception {
        final Attr attr = parse(serialized).getAttributeNodeNS(uri, localName);
        assertNotNull("@" + localName + " must be in " + uri + "; got " + serialized, attr);
    }

    /** XQTS Constr-inscope-3: foo is rebound on the new element. */
    @Test
    public void rebindsAPrefixTheElementBindsElsewhere() throws Exception {
        final String out = run("serialize(for $x in <parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/> "
                + "return <new xmlns:foo=\"" + EX + "\">{$x//@*:attr1}</new>)");
        assertAttributeIn(out, PARENT1, "attr1");
    }

    /** XQTS Constr-inscope-4: two attributes with the same prefix and different URIs. */
    @Test
    public void separatesTwoAttributesThatShareAPrefix() throws Exception {
        final String out = run("serialize(for $x in <inscope><parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/>"
                + "<parent2 xmlns:foo=\"" + PARENT2 + "\" foo:attr2=\"attr2\"/></inscope> "
                + "return <new>{$x//@*:attr1, $x//@*:attr2}</new>)");
        assertAttributeIn(out, PARENT1, "attr1");
        assertAttributeIn(out, PARENT2, "attr2");
    }

    /**
     * The #6704 scenario: a pooled context carrying the bindings another result left behind. The
     * element's own bindings decide, so they cannot mislead it.
     */
    @Test
    public void staleContextBindingsDoNotMisdirectTheAttribute() throws Exception {
        final String query = "serialize(for $x in <parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/> "
                + "return <new xmlns:foo=\"" + EX + "\">{$x//@*:attr1}</new>) (: pooled :)";
        run(query, context -> {
            context.declareInScopeNamespace("foo", PARENT1);
            context.declareInScopeNamespace("XXX", PARENT2);
        });
        assertAttributeIn(run(query), PARENT1, "attr1");
    }

    /** A prefix the element already binds to the attribute's URI is reused rather than inventing one. */
    @Test
    public void reusesAPrefixTheElementAlreadyBindsToTheUri() throws Exception {
        final String out = run("serialize(for $x in <parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/> "
                + "return <new xmlns:foo=\"" + EX + "\" xmlns:bar=\"" + PARENT1 + "\">{$x//@*:attr1}</new>)");
        assertAttributeIn(out, PARENT1, "attr1");
        assertEquals("bar", parse(out).getAttributeNodeNS(PARENT1, "attr1").getPrefix());
    }

    /** A generated prefix must not be one the element already uses for another URI. */
    @Test
    public void generatesAPrefixTheElementDoesNotUse() throws Exception {
        final String out = run("serialize(for $x in <parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/> "
                + "return <new xmlns:foo=\"" + EX + "\" xmlns:XXX=\"urn:taken\">{$x//@*:attr1}</new>)");
        assertAttributeIn(out, PARENT1, "attr1");
        assertEquals("XXX1", parse(out).getAttributeNodeNS(PARENT1, "attr1").getPrefix());
    }

    /** No conflict: the attribute keeps its prefix. */
    @Test
    public void keepsAPrefixThatDoesNotConflict() throws Exception {
        final String out = run("serialize(for $x in <parent1 xmlns:foo=\"" + PARENT1 + "\" foo:attr1=\"attr1\"/> "
                + "return <new>{$x//@*:attr1}</new>)");
        assertEquals("foo", parse(out).getAttributeNodeNS(PARENT1, "attr1").getPrefix());
    }
}
