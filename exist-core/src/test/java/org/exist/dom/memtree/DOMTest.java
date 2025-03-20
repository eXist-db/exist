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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.dom.QName;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.serializer.DOMSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author wolf
 */
@RunWith(ParallelRunner.class)
public class DOMTest {

    private final static String XML =
            "<test count=\"1\" value=\"5543\" xmlns:x=\"http://foo.org\" xmlns=\"http://bla.org\"><x:title id=\"s1\">My title</x:title><paragraph>First paragraph</paragraph>"
                    + "<section><title>subsection</title></section></test>";

    @Test
    public void documentBuilder() throws ParserConfigurationException, SAXException, IOException, TransformerException {
        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver();
        SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
        factory.setNamespaceAware(true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(receiver);
        reader.parse(new InputSource(new StringReader(XML)));

        Document doc = receiver.getDocument();
        Node node = doc.getFirstChild();
        assertNotNull(node);

        StringWriter writer = new StringWriter();
        DOMSerializer serializer = new DOMSerializer(writer, null);
        serializer.serialize(node);
        writer.toString();
    }

    @Test
    public void getChildNodes1() {
        MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("top", null, null), null);
        builder.characters("text");
        builder.endElement();
        builder.endDocument();
        DocumentImpl doc = builder.getDocument();
        Node top = doc.getFirstChild();
        assertEquals(Node.ELEMENT_NODE, top.getNodeType());
        assertEquals("top", top.getNodeName());
        assertEquals(1, top.getChildNodes().getLength());
    }

    @Test
    public void getChildNodes2() {
        MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("top", null, null), null);
        builder.startElement(new QName("child1", null, null), null);
        builder.endElement();
        builder.startElement(new QName("child2", null, null), null);
        builder.endElement();
        builder.endElement();
        builder.endDocument();
        DocumentImpl doc = builder.getDocument();
        Node top = doc.getFirstChild();
        assertEquals(Node.ELEMENT_NODE, top.getNodeType());
        assertEquals("top", top.getNodeName());
        assertEquals(2, top.getChildNodes().getLength());
    }

    @Test
    public void getElementsByTagName() {
        MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("xquery", null, null), null);
        builder.startElement(new QName("builtin-modules", null, null), null);

        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute(null, "class", "class", "string", "org.exist.xquery.functions.util.UtilModule");
        attrs.addAttribute(null, "uri", "uri", "string", "http://exist-db.org/xquery/util");
        builder.startElement(new QName("module", null, null), attrs);
        builder.endElement();

        attrs = new AttributesImpl();
        attrs.addAttribute(null, "class", "class", "string", "org.exist.xquery.functions.request.RequestModule");
        attrs.addAttribute(null, "uri", "uri", "string", "http://exist-db.org/xquery/request");
        builder.startElement(new QName("module", null, null), attrs);

        attrs = new AttributesImpl();
        attrs.addAttribute(null, "name", "name", "string", "stream");
        attrs.addAttribute(null, "value", "value", "string", "true");
        builder.startElement(new QName("parameter", null, null), attrs);
        builder.endElement();

        builder.endElement();

        attrs = new AttributesImpl();
        attrs.addAttribute(null, "class", "class", "string", "org.exist.xquery.functions.util.ResponseModule");
        attrs.addAttribute(null, "uri", "uri", "string", "http://exist-db.org/xquery/response");
        builder.startElement(new QName("module", null, null), attrs);
        builder.endElement();

        attrs = new AttributesImpl();
        attrs.addAttribute(null, "class", "class", "string", "org.exist.xquery.functions.util.SessionModule");
        attrs.addAttribute(null, "uri", "uri", "string", "http://exist-db.org/xquery/session");
        builder.startElement(new QName("module", null, null), attrs);
        builder.endElement();

        builder.endElement();
        builder.endElement();
        builder.endDocument();

        DocumentImpl doc = builder.getDocument();

        Node nXQuery = doc.getFirstChild();
        assertTrue(nXQuery.getNodeType() == Node.ELEMENT_NODE);
        assertTrue("xquery".equals(nXQuery.getLocalName()));

        Node nBuiltinModules = nXQuery.getFirstChild();
        assertTrue(nBuiltinModules.getNodeType() == Node.ELEMENT_NODE);
        assertTrue("builtin-modules".equals(nBuiltinModules.getLocalName()));

        NodeList nlModules = nBuiltinModules.getChildNodes();
        for (int i = 0; i < nlModules.getLength(); i++) {
            Node nModule = nlModules.item(i);

            assertTrue(nModule.getNodeType() == Node.ELEMENT_NODE);
            assertTrue("module".equals(nModule.getLocalName()));

            Element eModule = (Element) nModule;
            NodeList nlParameter = eModule.getElementsByTagName("parameter");

            if ("org.exist.xquery.functions.request.RequestModule".equals(eModule.getAttribute("class"))) {
                assertEquals(1, nlParameter.getLength());
            } else {
                assertEquals(0, nlParameter.getLength());
            }
        }
    }

//	public void print(Node node) {
//		while (node != null) {
//			switch (node.getNodeType()) {
//				case Node.ELEMENT_NODE :
//					System.out.println('<' + node.getNodeName() + '>');
//					break;
//				case Node.TEXT_NODE :
//					System.out.println(node.getNodeValue());
//					break;
//				default :
//					System.out.println("unknown node type");
//			}
//			if (node.hasChildNodes())
//				print(node.getFirstChild());
//			node = node.getNextSibling();
//		}
//	}
}
