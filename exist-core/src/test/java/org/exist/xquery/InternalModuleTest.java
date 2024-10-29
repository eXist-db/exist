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

import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.LocalXMLResource;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.exist.xquery.InternalModuleTest.TestModuleWithVariables.*;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class InternalModuleTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    @Rule
    public final ExistXmldbEmbeddedServer existServer = new ExistXmldbEmbeddedServer(true, true, true);

    private static final String EOL = System.getProperty("line.separator");

    @Test
    public void moduleVariables() throws XMLDBException {
        final Source querySource = new StringSource(getModuleVariableQuery("org.exist.xquery.InternalModuleTest$TestModuleWithVariables"));
        final EXistXQueryService queryService = existServer.getRoot().getService(EXistXQueryService.class);

        moduleVariablesQuery(queryService, querySource, COUNTER.get());
    }

    /**
     * Similar to {@link #moduleVariables()} but
     * re-executes the query to ensure on subsequent
     * invocations, reusing the cached query (and query
     * context) do not cause problems.
     */
    @Test
    public void reusedModuleVariables() throws XMLDBException {
        final Source querySource = new StringSource(getModuleVariableQuery("org.exist.xquery.InternalModuleTest$TestModuleWithVariables"));
        final EXistXQueryService queryService = existServer.getRoot().getService(EXistXQueryService.class);

        moduleVariablesQuery(queryService, querySource, COUNTER.get());
        moduleVariablesQuery(queryService, querySource, COUNTER.get());
        moduleVariablesQuery(queryService, querySource, COUNTER.get());
    }

    /**
     * The $request:request, $session:session, and
     * $response:response variables were removed in eXist-db 5.0.0.
     */
    @Test
    public void requestResponseSessionVariables_4_x_x_Api() throws XMLDBException {
        final Source querySource = new StringSource(
                "document{" + EOL +
                        "  <vars>" + EOL +
                        "    <request>{empty($request:request)}</request>" + EOL +
                        "    <session>{empty($session:session)}</session>" + EOL +
                        "    <response>{empty($response:response)}</response>" + EOL +
                        "  </vars>" + EOL +
                        "}"

        );
        final EXistXQueryService queryService = existServer.getRoot().getService(EXistXQueryService.class);

        try {
            requestResponseSessionVariablesQuery_4_x_X_Api(queryService, querySource);
            fail("Expected XQuery error XPST0008");
        } catch (final XMLDBException e) {
            assertTrue(e.getCause() instanceof XPathException);
            final XPathException xpe = (XPathException)e.getCause();
            assertEquals(ErrorCodes.XPST0008, xpe.getErrorCode());
        }
    }

    private void requestResponseSessionVariablesQuery_4_x_X_Api(final EXistXQueryService queryService, final Source query) throws XMLDBException {
        final ResourceSet result = queryService.execute(query);  // this variation of execute will use the XQueryPool for caching

        assertNotNull(result);
        assertEquals(1, result.getSize());

        final LocalXMLResource resource = (LocalXMLResource)result.getResource(0);
        assertNotNull(resource);

        final Node actualDoc = resource.getContentAsDOM();

        final javax.xml.transform.Source expected = Input.fromString("<vars><request>XPDY0002</request><session>XPDY0002</session><response>XPDY0002</response></vars>").build();
        final javax.xml.transform.Source actual = Input.fromNode(actualDoc).build();

        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    private static String getModuleVariableQuery(final String javaClass) {
        return
                "import module namespace " + PREFIX + " = '" + NS + "' at 'java:" + javaClass + "';" + EOL
                + "document {" + EOL
                + "  <variables>" + EOL
                + "    <var1>{$" + PREFIX + ":var1}</var1>" + EOL
                + "  </variables>" + EOL
                + "}" + EOL;
    }

    private void moduleVariablesQuery(final EXistXQueryService queryService, final Source query, final long expectedCount) throws XMLDBException {

        final ResourceSet result = queryService.execute(query);  // this variation of execute will use the XQueryPool for caching

        assertNotNull(result);
        assertEquals(1, result.getSize());

        final LocalXMLResource resource = (LocalXMLResource)result.getResource(0);
        assertNotNull(resource);

        final Node actualDoc = resource.getContentAsDOM();

        final javax.xml.transform.Source expected = Input.fromString("<variables><var1>" + expectedCount + "</var1></variables>").build();
        final javax.xml.transform.Source actual = Input.fromNode(actualDoc).build();

        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    public static class TestModuleWithVariables extends AbstractInternalModule {
        public static final String NS = "http://TestModuleWithVariables";
        public static final String PREFIX = "tmwv";

        public static final QName VAR1_NAME = new QName("var1", NS);

        public TestModuleWithVariables(final Map<String, List<?>> parameters) throws XPathException {
            super(new FunctionDef[0], parameters);
        }

        @Override
        public void prepare(final XQueryContext context) throws XPathException {
            declareVariable(VAR1_NAME, COUNTER.getAndIncrement());
        }

        @Override
        public String getNamespaceURI() {
            return NS;
        }

        @Override
        public String getDefaultPrefix() {
            return PREFIX;
        }

        @Override
        public String getDescription() {
            return "mod1";
        }

        @Override
        public String getReleaseVersion() {
            return "99";
        }
    }
}
