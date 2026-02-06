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

package org.exist.test.runner;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.source.FileSource;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that util:inspect-function returns line and source attributes for user-defined functions (plan item 7).
 */
public class InspectLineSourceTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void inspectFunctionReturnsLineAndSourceForUDF() throws EXistException, PermissionDeniedException, XPathException, IOException, DatabaseConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Path path = Paths.get("src/test/resources/org/exist/test/runner/inspect-line-source-test.xq").toAbsolutePath();
        if (!Files.exists(path)) {
            throw new AssertionError("Test resource missing: " + path);
        }
        final Sequence result = AbstractTestRunner.executeQuery(pool, new FileSource(path, UTF_8, false), Collections.emptyList(), path.getParent());
        assertNotNull("query should return a result", result);
        assertTrue("query should return at least one item", result.getItemCount() >= 1);
        final Node first = ((NodeValue) result.itemAt(0)).getNode();
        assertEquals("first result should be an element", Node.ELEMENT_NODE, first.getNodeType());
        final Element func = (Element) first;
        assertTrue("function element should have @line for UDF", func.hasAttribute("line"));
        final String lineStr = func.getAttribute("line");
        assertTrue("line should be a positive number", Integer.parseInt(lineStr) > 0);
        assertTrue("function element should have @source for UDF", func.hasAttribute("source"));
        assertTrue("source should be non-empty", !func.getAttribute("source").isEmpty());
    }
}
