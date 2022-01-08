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
package org.exist.xquery.functions.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author Casey Jordan
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ExpandTest {

    private static final String EOL = System.getProperty("line.separator");

    private static final String DOC1_CONTENT = "<doc1>doc1</doc1>";
    private static final String DOC2_CONTENT = "<!-- comment 1 before --><!-- comment 2 before -->" + EOL + "<doc2>doc2</doc2>";
    private static final String DOC3_CONTENT = "<doc3 foo=\"bar\">doc3</doc3>";
    private static final String DOC4_CONTENT = "<doc4 xmlns:x=\"http://x\" x:foo=\"bar\">doc4</doc4>";

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void setup() throws XMLDBException {
        final Collection expandTestCol = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "expand-test");
        ExistXmldbEmbeddedServer.storeResource(expandTestCol, "doc1.xml", DOC1_CONTENT.getBytes(UTF_8));
        ExistXmldbEmbeddedServer.storeResource(expandTestCol, "doc2.xml", DOC2_CONTENT.getBytes(UTF_8));
        ExistXmldbEmbeddedServer.storeResource(expandTestCol, "doc3.xml", DOC3_CONTENT.getBytes(UTF_8));
        ExistXmldbEmbeddedServer.storeResource(expandTestCol, "doc4.xml", DOC4_CONTENT.getBytes(UTF_8));
    }

    @Test
    public void expandWithDefaultNS() throws XMLDBException {
    	final String expected = "<ok xmlns=\"some\">\n    <concept xmlns=\"\"/>\n</ok>";

        String query = "" +
                "let $doc-path := xmldb:store('/db', 'test.xml', <concept/>)\n" +
                "let $doc := doc($doc-path)\n" +
                "return\n" +
                "<ok xmlns='some'>\n" +
                "{util:expand($doc)}\n" +
                "</ok>";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        query = "" +
                "let $doc-path := xmldb:store('/db', 'test.xml', <concept/>)\n" +
                "let $doc := doc($doc-path)\n" +
                "return\n" +
                "<ok xmlns='some'>\n" +
                "{$doc}\n" +
                "</ok>";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);
    }

    @Test
    public void expandPersistentDom() throws XMLDBException {
        final String query = "util:expand(doc('/db/expand-test/doc1.xml'))";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals(DOC1_CONTENT, r);
    }

    @Test
    public void expandPersistentDomCommentsFirst() throws XMLDBException {
        final String query = "util:expand(doc('/db/expand-test/doc2.xml'))";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals(DOC2_CONTENT, r);
    }

    @Test
    public void expandPersistentDomAttr() throws XMLDBException {
        final String query = "util:expand(doc('/db/expand-test/doc3.xml')/doc3/@foo)";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final Resource res = result.getResource(0);
        assertEquals(XMLResource.RESOURCE_TYPE, res.getResourceType());
        final XMLResource xmlRes = (XMLResource) res;
        final Node node = xmlRes.getContentAsDOM();
        assertEquals(Node.ATTRIBUTE_NODE, node.getNodeType());
        assertNull(node.getNamespaceURI());
        assertEquals("foo", node.getNodeName());
        assertEquals("bar", node.getNodeValue());
    }

    @Test
    public void expandPersistentDomAttrNs() throws XMLDBException {
        final String query = "declare namespace x = \"http://x\";\n" +
                "util:expand(doc('/db/expand-test/doc4.xml')/doc4/@x:foo)";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final Resource res = result.getResource(0);
        assertEquals(XMLResource.RESOURCE_TYPE, res.getResourceType());
        final XMLResource xmlRes = (XMLResource) res;
        final Node node = xmlRes.getContentAsDOM();
        assertEquals(Node.ATTRIBUTE_NODE, node.getNodeType());
        assertEquals("http://x", node.getNamespaceURI());
        assertEquals("foo", node.getNodeName());
        assertEquals("bar", node.getNodeValue());
    }
}
