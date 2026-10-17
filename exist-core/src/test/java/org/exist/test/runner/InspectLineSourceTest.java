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
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that util:inspect-function returns line and source attributes for user-defined functions.
 */
public class InspectLineSourceTest {

    @RegisterExtension
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void inspectFunctionReturnsLineAndSourceForUDF() throws EXistException, PermissionDeniedException, XPathException, IOException, DatabaseConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Path path = Path.of("src/test/resources/org/exist/test/runner/inspect-line-source-test.xqm").toAbsolutePath();
        if (!Files.exists(path)) {
            throw new AssertionError("Test resource missing: " + path);
        }
        final Sequence result = AbstractTestRunner.executeQuery(pool, new FileSource(path, UTF_8, false), Collections.emptyList(), path.getParent());
        assertNotNull(result, "query should return a result");
        assertTrue(result.getItemCount() >= 1, "query should return at least one item");
        final Node first = ((NodeValue) result.itemAt(0)).getNode();
        assertEquals(Node.ELEMENT_NODE, first.getNodeType(), "first result should be an element");
        final Element func = (Element) first;
        assertTrue(func.hasAttribute("line"), "function element should have @line for UDF");
        final String lineStr = func.getAttribute("line");
        assertTrue(Integer.parseInt(lineStr) > 0, "line should be a positive number");
        assertTrue(func.hasAttribute("source"), "function element should have @source for UDF");
        assertFalse(func.getAttribute("source").isEmpty(), "source should be non-empty");
    }
}
