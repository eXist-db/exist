/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.LocalXMLResource;
import org.junit.ClassRule;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class InternalModuleTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    @ClassRule
    public static final ExistXmldbEmbeddedServer existServer = new ExistXmldbEmbeddedServer(true, true, true);

    private static final String EOL = System.getProperty("line.separator");
    private static final String MODULE_VARIABLE_QUERY =
            "import module namespace " + PREFIX + " = '" + NS + "' at 'java:org.exist.xquery.InternalModuleTest$TestModuleWithVariables';" + EOL
            + "document {" + EOL
            + "  <variables>" + EOL
            + "    <var1>{$" + PREFIX + ":var1}</var1>" + EOL
            + "  </variables>" + EOL
            + "}" + EOL;

    @Test
    public void moduleVariables() throws XMLDBException {
        final Source querySource = new StringSource(MODULE_VARIABLE_QUERY);
        final EXistXQueryService queryService = (EXistXQueryService)existServer.getRoot().getService("XQueryService", "1.0");

        moduleVariablesQuery(queryService, querySource);
    }

    /**
     * Similar to {@link #moduleVariables()} but
     * re-executes the query to ensure on subsequent
     * invocations, reusing the cached query (and query
     * context) do not cause problems.
     */
    @Test
    public void reusedModuleVariables() throws XMLDBException {
        final Source querySource = new StringSource(MODULE_VARIABLE_QUERY);
        final EXistXQueryService queryService = (EXistXQueryService)existServer.getRoot().getService("XQueryService", "1.0");

        moduleVariablesQuery(queryService, querySource);
        moduleVariablesQuery(queryService, querySource);
        moduleVariablesQuery(queryService, querySource);
    }

    private void moduleVariablesQuery(final EXistXQueryService queryService, final Source query) throws XMLDBException {
        final long expectedVar1 = COUNTER.get();

        final ResourceSet result = queryService.execute(query);  // this variation of execute will use the XQueryPool for caching

        assertNotNull(result);
        assertEquals(1, result.getSize());

        final LocalXMLResource resource = (LocalXMLResource)result.getResource(0);
        assertNotNull(resource);

        final Node actualDoc = resource.getContentAsDOM();

        final javax.xml.transform.Source expected = Input.fromString("<variables><var1>" + expectedVar1 + "</var1></variables>").build();
        final javax.xml.transform.Source actual = Input.fromNode(actualDoc).build();

        final Diff diff = DiffBuilder.compare(actual)
                .withTest(expected)
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
