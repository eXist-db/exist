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

import com.googlecode.junittoolbox.ParallelRunner;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.Namespaces;
import org.exist.util.ExistSAXParserFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@RunWith(ParallelRunner.class)
public class MemtreeTest {

    private final static String XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!-- comment before doc 1 -->\n" +
                    "<?pi-before-doc-1?>\n" +
                    "<!-- comment before doc 2 -->\n" +
                    "<?pi-before-doc-2?>\n" +
                    "<doc-element>\n" +
                    "    <!-- comment before e1 -->\n" +
                    "    <e1 t=\"2\">\n" +
                    "        <?pi-before-e1_1?>\n" +
                    "        <e1_1 t=\"3\">text 1</e1_1>\n" +
                    "        <e1_2 t=\"3\" x=\"y\">text 2</e1_2>\n" +
                    "    </e1>\n" +
                    "    <!-- comment after e1 -->\n" +
                    "</doc-element>\n" +
                    "<?pi-after-doc-1?>\n" +
                    "<!-- comment after doc 1 -->\n" +
                    "<?pi-after-doc-2?>\n" +
                    "<!-- comment after doc 2 -->\n" +
                    "<?pi-after-doc-3?>";

    @Test
    public void size() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(27, doc.getSize());
    }

    @Test
    public void getLastNode() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(26, doc.getLastNode());
    }

    @Test
    public void getChildCountFor() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(10, doc.getChildCountFor(0));  // children of the document node
        assertEquals(0, doc.getChildCountFor(1));   // children of <!-- comment before doc 1 -->
        assertEquals(0, doc.getChildCountFor(2));   // children of <?pi-before-doc-1?>
        assertEquals(0, doc.getChildCountFor(3));   // children of <!-- comment before doc 2 -->
        assertEquals(0, doc.getChildCountFor(4));   // children of <?pi-before-doc-2?>
        assertEquals(7, doc.getChildCountFor(5));   // children of doc-element
        assertEquals(0, doc.getChildCountFor(6));   // children of doc-element/text()[1]
        assertEquals(0, doc.getChildCountFor(7));   // children of <!-- comment before e1 -->
        assertEquals(0, doc.getChildCountFor(8));   // children of doc-element/text()[2]
        assertEquals(7, doc.getChildCountFor(9));   // children of e1
        assertEquals(0, doc.getChildCountFor(10));  // children of e1/text()[1]
        assertEquals(0, doc.getChildCountFor(11));  // children of <?pi-before-e1_1?>
        assertEquals(0, doc.getChildCountFor(12));  // children of e1/text()[2]
        assertEquals(1, doc.getChildCountFor(13));  // children of e1_1
        assertEquals(0, doc.getChildCountFor(14));  // children of e1_1/text()[1]
        assertEquals(0, doc.getChildCountFor(15));  // children of e1/text()[3]
        assertEquals(1, doc.getChildCountFor(16));  // children of e1_2
        assertEquals(0, doc.getChildCountFor(17));  // children of e1_2/text()[1]
        assertEquals(0, doc.getChildCountFor(18));  // children of e1/text()[4]
        assertEquals(0, doc.getChildCountFor(19));  // children of doc-element/text()[3]
        assertEquals(0, doc.getChildCountFor(20));  // children of <!-- comment after e1 -->
        assertEquals(0, doc.getChildCountFor(21));  // children of doc-element/text()[4]
        assertEquals(0, doc.getChildCountFor(22));  // children of <?pi-after-doc-1?>
        assertEquals(0, doc.getChildCountFor(23));  // children of <!-- comment after doc 1 -->
        assertEquals(0, doc.getChildCountFor(24));  // children of <?pi-after-doc-2?>
        assertEquals(0, doc.getChildCountFor(25));  // children of <!-- comment after doc 2 -->
        assertEquals(0, doc.getChildCountFor(26));  // children of <?pi-after-doc-3?>
    }

    @Test
    public void getNodeType() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(Node.DOCUMENT_NODE, doc.getNodeType(0));                   // type of the document node
        assertEquals(Node.COMMENT_NODE, doc.getNodeType(1));                    // type of <!-- comment before doc 1 -->
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, doc.getNodeType(2));     // type of <?pi-before-doc-1?>
        assertEquals(Node.COMMENT_NODE, doc.getNodeType(3));                    // type of <!-- comment before doc 2 -->
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, doc.getNodeType(4));     // type of <?pi-before-doc-2?>
        assertEquals(Node.ELEMENT_NODE, doc.getNodeType(5));                    // type of doc-element
        assertEquals(Node.TEXT_NODE, doc.getNodeType(6));                       // type of doc-element/text()[1]
        assertEquals(Node.COMMENT_NODE, doc.getNodeType(7));                    // type of <!-- comment before e1 -->
        assertEquals(Node.TEXT_NODE, doc.getNodeType(8));                       // type of doc-element/text()[2]
        assertEquals(Node.ELEMENT_NODE, doc.getNodeType(9));                    // type of e1
        assertEquals(Node.TEXT_NODE, doc.getNodeType(10));                      // type of e1/text()[1]
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, doc.getNodeType(11));    // type of <?pi-before-e1_1?>
        assertEquals(Node.TEXT_NODE, doc.getNodeType(12));                      // type of e1/text()[2]
        assertEquals(Node.ELEMENT_NODE, doc.getNodeType(13));                   // type of e1_1
        assertEquals(Node.TEXT_NODE, doc.getNodeType(14));                      // type of e1_1/text()[1]
        assertEquals(Node.TEXT_NODE, doc.getNodeType(15));                      // type of e1/text()[3]
        assertEquals(Node.ELEMENT_NODE, doc.getNodeType(16));                   // type of e1_2
        assertEquals(Node.TEXT_NODE, doc.getNodeType(17));                      // type of e1_2/text()[1]
        assertEquals(Node.TEXT_NODE, doc.getNodeType(18));                      // type of e1/text()[4]
        assertEquals(Node.TEXT_NODE, doc.getNodeType(19));                      // type of doc-element/text()[3]
        assertEquals(Node.COMMENT_NODE, doc.getNodeType(20));                   // type of <!-- comment after e1 -->
        assertEquals(Node.TEXT_NODE, doc.getNodeType(21));                      // type of doc-element/text()[4]
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, doc.getNodeType(22));    // type of <?pi-after-doc-1?>
        assertEquals(Node.COMMENT_NODE, doc.getNodeType(23));                   // type of <!-- comment after doc 1 -->
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, doc.getNodeType(24));    // type of <?pi-after-doc-2?>
        assertEquals(Node.COMMENT_NODE, doc.getNodeType(25));                   // type of <!-- comment after doc 2 -->
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, doc.getNodeType(26));    // type of <?pi-after-doc-3?>
    }

    @Test
    public void getTreeLevel() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(0, doc.getTreeLevel(0));   // depth of the document node
        assertEquals(1, doc.getTreeLevel(1));   // depth of <!-- comment before doc 1 -->
        assertEquals(1, doc.getTreeLevel(2));   // depth of <?pi-before-doc-1?>
        assertEquals(1, doc.getTreeLevel(3));   // depth of <!-- comment before doc 2 -->
        assertEquals(1, doc.getTreeLevel(4));   // depth of <?pi-before-doc-2?>
        assertEquals(1, doc.getTreeLevel(5));   // depth of doc-element
        assertEquals(2, doc.getTreeLevel(6));   // depth of doc-element/text()[1]
        assertEquals(2, doc.getTreeLevel(7));   // depth of <!-- comment before e1 -->
        assertEquals(2, doc.getTreeLevel(8));   // depth of doc-element/text()[2]
        assertEquals(2, doc.getTreeLevel(9));   // depth of e1
        assertEquals(3, doc.getTreeLevel(10));  // depth of e1/text()[1]
        assertEquals(3, doc.getTreeLevel(11));  // depth of <?pi-before-e1_1?>
        assertEquals(3, doc.getTreeLevel(12));  // depth of e1/text()[2]
        assertEquals(3, doc.getTreeLevel(13));  // depth of e1_1
        assertEquals(4, doc.getTreeLevel(14));  // depth of e1_1/text()[1]
        assertEquals(3, doc.getTreeLevel(15));  // depth of e1/text()[3]
        assertEquals(3, doc.getTreeLevel(16));  // depth of e1_2
        assertEquals(4, doc.getTreeLevel(17));  // depth of e1_2/text()[1]
        assertEquals(3, doc.getTreeLevel(18));  // depth of e1/text()[4]
        assertEquals(2, doc.getTreeLevel(19));  // depth of doc-element/text()[3]
        assertEquals(2, doc.getTreeLevel(20));  // depth of <!-- comment after e1 -->
        assertEquals(2, doc.getTreeLevel(21));  // depth of doc-element/text()[4]
        assertEquals(1, doc.getTreeLevel(22));  // depth of <?pi-after-doc-1?>
        assertEquals(1, doc.getTreeLevel(23));  // depth of <!-- comment after doc 1 -->
        assertEquals(1, doc.getTreeLevel(24));  // depth of <?pi-after-doc-2?>
        assertEquals(1, doc.getTreeLevel(25));  // depth of <!-- comment after doc 2 -->
        assertEquals(1, doc.getTreeLevel(26));  // depth of <?pi-after-doc-3?>
    }

    @Test
    public void getAttributesCountFor() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(0, doc.getAttributesCountFor(0));   // attributes count of the document node
        assertEquals(0, doc.getAttributesCountFor(1));   // attributes count of <!-- comment before doc 1 -->
        assertEquals(0, doc.getAttributesCountFor(2));   // attributes count of <?pi-before-doc-1?>
        assertEquals(0, doc.getAttributesCountFor(3));   // attributes count of <!-- comment before doc 2 -->
        assertEquals(0, doc.getAttributesCountFor(4));   // attributes count of <?pi-before-doc-2?>
        assertEquals(0, doc.getAttributesCountFor(5));   // attributes count of doc-element
        assertEquals(0, doc.getAttributesCountFor(6));   // attributes count of doc-element/text()[1]
        assertEquals(0, doc.getAttributesCountFor(7));   // attributes count of <!-- comment before e1 -->
        assertEquals(0, doc.getAttributesCountFor(8));   // attributes count of doc-element/text()[2]
        assertEquals(1, doc.getAttributesCountFor(9));   // attributes count of e1
        assertEquals(0, doc.getAttributesCountFor(10));  // attributes count of e1/text()[1]
        assertEquals(0, doc.getAttributesCountFor(11));  // attributes count of <?pi-before-e1_1?>
        assertEquals(0, doc.getAttributesCountFor(12));  // attributes count of e1/text()[2]
        assertEquals(1, doc.getAttributesCountFor(13));  // attributes count of e1_1
        assertEquals(0, doc.getAttributesCountFor(14));  // attributes count of e1_1/text()[1]
        assertEquals(0, doc.getAttributesCountFor(15));  // attributes count of e1/text()[3]
        assertEquals(2, doc.getAttributesCountFor(16));  // attributes count of e1_2
        assertEquals(0, doc.getAttributesCountFor(17));  // attributes count of e1_2/text()[1]
        assertEquals(0, doc.getAttributesCountFor(18));  // attributes count of e1/text()[4]
        assertEquals(0, doc.getAttributesCountFor(19));  // attributes count of doc-element/text()[3]
        assertEquals(0, doc.getAttributesCountFor(20));  // attributes count of <!-- comment after e1 -->
        assertEquals(0, doc.getAttributesCountFor(21));  // attributes count of doc-element/text()[4]
        assertEquals(0, doc.getAttributesCountFor(22));  // attributes count of <?pi-after-doc-1?>
        assertEquals(0, doc.getAttributesCountFor(23));  // attributes count of <!-- comment after doc 1 -->
        assertEquals(0, doc.getAttributesCountFor(24));  // attributes count of <?pi-after-doc-2?>
        assertEquals(0, doc.getAttributesCountFor(25));  // attributes count of <!-- comment after doc 2 -->
        assertEquals(0, doc.getAttributesCountFor(26));  // attributes count of <?pi-after-doc-3?>
    }

    @Test
    public void getNamespacesCountFor() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(0, doc.getNamespacesCountFor(0));   // namespaces count of the document node
        assertEquals(0, doc.getNamespacesCountFor(1));   // namespaces count of <!-- comment before doc 1 -->
        assertEquals(0, doc.getNamespacesCountFor(2));   // namespaces count of <?pi-before-doc-1?>
        assertEquals(0, doc.getNamespacesCountFor(3));   // namespaces count of <!-- comment before doc 2 -->
        assertEquals(0, doc.getNamespacesCountFor(4));   // namespaces count of <?pi-before-doc-2?>
        assertEquals(0, doc.getNamespacesCountFor(5));   // namespaces count of doc-element
        assertEquals(0, doc.getNamespacesCountFor(6));   // namespaces count of doc-element/text()[1]
        assertEquals(0, doc.getNamespacesCountFor(7));   // namespaces count of <!-- comment before e1 -->
        assertEquals(0, doc.getNamespacesCountFor(8));   // namespaces count of doc-element/text()[2]
        assertEquals(0, doc.getNamespacesCountFor(9));   // namespaces count of e1
        assertEquals(0, doc.getNamespacesCountFor(10));  // namespaces count of e1/text()[1]
        assertEquals(0, doc.getNamespacesCountFor(11));  // namespaces count of <?pi-before-e1_1?>
        assertEquals(0, doc.getNamespacesCountFor(12));  // namespaces count of e1/text()[2]
        assertEquals(0, doc.getNamespacesCountFor(13));  // namespaces count of e1_1
        assertEquals(0, doc.getNamespacesCountFor(14));  // namespaces count of e1_1/text()[1]
        assertEquals(0, doc.getNamespacesCountFor(15));  // namespaces count of e1/text()[3]
        assertEquals(0, doc.getNamespacesCountFor(16));  // namespaces count of e1_2
        assertEquals(0, doc.getNamespacesCountFor(17));  // namespaces count of e1_2/text()[1]
        assertEquals(0, doc.getNamespacesCountFor(18));  // namespaces count of e1/text()[4]
        assertEquals(0, doc.getNamespacesCountFor(19));  // namespaces count of doc-element/text()[3]
        assertEquals(0, doc.getNamespacesCountFor(20));  // namespaces count of <!-- comment after e1 -->
        assertEquals(0, doc.getNamespacesCountFor(21));  // namespaces count of doc-element/text()[4]
        assertEquals(0, doc.getNamespacesCountFor(22));  // namespaces count of <?pi-after-doc-1?>
        assertEquals(0, doc.getNamespacesCountFor(23));  // namespaces count of <!-- comment after doc 1 -->
        assertEquals(0, doc.getNamespacesCountFor(24));  // namespaces count of <?pi-after-doc-2?>
        assertEquals(0, doc.getNamespacesCountFor(25));  // namespaces count of <!-- comment after doc 2 -->
        assertEquals(0, doc.getNamespacesCountFor(26));  // namespaces count of <?pi-after-doc-3?>
    }

    @Test
    public void getFirstChildFor() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(1, doc.getFirstChildFor(0));       // first child of the document node
        assertEquals(-1, doc.getFirstChildFor(1));      // first child of <!-- comment before doc 1 -->
        assertEquals(-1, doc.getFirstChildFor(2));      // first child of <?pi-before-doc-1?>
        assertEquals(-1, doc.getFirstChildFor(3));      // first child of <!-- comment before doc 2 -->
        assertEquals(-1, doc.getFirstChildFor(4));      // first child of <?pi-before-doc-2?>
        assertEquals(6, doc.getFirstChildFor(5));       // first child of doc-element
        assertEquals(-1, doc.getFirstChildFor(6));      // first child of doc-element/text()[1]
        assertEquals(-1, doc.getFirstChildFor(7));      // first child of <!-- comment before e1 -->
        assertEquals(-1, doc.getFirstChildFor(8));      // first child of doc-element/text()[2]
        assertEquals(10, doc.getFirstChildFor(9));      // first child of e1
        assertEquals(-1, doc.getFirstChildFor(10));     // first child of e1/text()[1]
        assertEquals(-1, doc.getFirstChildFor(11));     // first child of <?pi-before-e1_1?>
        assertEquals(-1, doc.getFirstChildFor(12));     // first child of e1/text()[2]
        assertEquals(14, doc.getFirstChildFor(13));     // first child of e1_1
        assertEquals(-1, doc.getFirstChildFor(14));     // first child of e1_1/text()[1]
        assertEquals(-1, doc.getFirstChildFor(15));     // first child of e1/text()[3]
        assertEquals(17, doc.getFirstChildFor(16));     // first child of e1_2
        assertEquals(-1, doc.getFirstChildFor(17));     // first child of e1_2/text()[1]
        assertEquals(-1, doc.getFirstChildFor(18));     // first child of e1/text()[4]
        assertEquals(-1, doc.getFirstChildFor(19));     // first child of doc-element/text()[3]
        assertEquals(-1, doc.getFirstChildFor(20));     // first child of <!-- comment after e1 -->
        assertEquals(-1, doc.getFirstChildFor(21));     // first child of doc-element/text()[4]
        assertEquals(-1, doc.getFirstChildFor(22));     // first child of <?pi-after-doc-1?>
        assertEquals(-1, doc.getFirstChildFor(23));     // first child of <!-- comment after doc 1 -->
        assertEquals(-1, doc.getFirstChildFor(24));     // first child of <?pi-after-doc-2?>
        assertEquals(-1, doc.getFirstChildFor(25));     // first child of <!-- comment after doc 2 -->
        assertEquals(-1, doc.getFirstChildFor(26));     // first child of <?pi-after-doc-3?>
    }

    @Test
    public void getNextSiblingFor() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(-1, doc.getNextSiblingFor(0));     // next sibling of the document node
        assertEquals(2, doc.getNextSiblingFor(1));      // next sibling of <!-- comment before doc 1 -->
        assertEquals(3, doc.getNextSiblingFor(2));      // next sibling of <?pi-before-doc-1?>
        assertEquals(4, doc.getNextSiblingFor(3));      // next sibling of <!-- comment before doc 2 -->
        assertEquals(5, doc.getNextSiblingFor(4));      // next sibling of <?pi-before-doc-2?>
        assertEquals(22, doc.getNextSiblingFor(5));     // next sibling of doc-element
        assertEquals(7, doc.getNextSiblingFor(6));      // next sibling of doc-element/text()[1]
        assertEquals(8, doc.getNextSiblingFor(7));      // next sibling of <!-- comment before e1 -->
        assertEquals(9, doc.getNextSiblingFor(8));      // next sibling of doc-element/text()[2]
        assertEquals(19, doc.getNextSiblingFor(9));     // next sibling of e1
        assertEquals(11, doc.getNextSiblingFor(10));    // next sibling of e1/text()[1]
        assertEquals(12, doc.getNextSiblingFor(11));    // next sibling of <?pi-before-e1_1?>
        assertEquals(13, doc.getNextSiblingFor(12));    // next sibling of e1/text()[2]
        assertEquals(15, doc.getNextSiblingFor(13));    // next sibling of e1_1
        assertEquals(-1, doc.getNextSiblingFor(14));    // next sibling of e1_1/text()[1]
        assertEquals(16, doc.getNextSiblingFor(15));    // next sibling of e1/text()[3]
        assertEquals(18, doc.getNextSiblingFor(16));    // next sibling of e1_2
        assertEquals(-1, doc.getNextSiblingFor(17));    // next sibling of e1_2/text()[1]
        assertEquals(-1, doc.getNextSiblingFor(18));    // next sibling of e1/text()[4]
        assertEquals(20, doc.getNextSiblingFor(19));    // next sibling of doc-element/text()[3]
        assertEquals(21, doc.getNextSiblingFor(20));    // next sibling of <!-- comment after e1 -->
        assertEquals(-1, doc.getNextSiblingFor(21));    // next sibling of doc-element/text()[4]
        assertEquals(23, doc.getNextSiblingFor(22));    // next sibling of <?pi-after-doc-1?>
        assertEquals(24, doc.getNextSiblingFor(23));    // next sibling of <!-- comment after doc 1 -->
        assertEquals(25, doc.getNextSiblingFor(24));    // next sibling of <?pi-after-doc-2?>
        assertEquals(26, doc.getNextSiblingFor(25));    // next sibling of <!-- comment after doc 2 -->
        assertEquals(-1, doc.getNextSiblingFor(26));    // next sibling of <?pi-after-doc-3?>
    }

    @Test
    public void getParentNodeFor() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertEquals(-1, doc.getParentNodeFor(0));      // parent of the document node
        assertEquals(0, doc.getParentNodeFor(1));       // parent of <!-- comment before doc 1 -->
        assertEquals(0, doc.getParentNodeFor(2));       // parent of <?pi-before-doc-1?>
        assertEquals(0, doc.getParentNodeFor(3));       // parent of <!-- comment before doc 2 -->
        assertEquals(0, doc.getParentNodeFor(4));       // parent of <?pi-before-doc-2?>
        assertEquals(0, doc.getParentNodeFor(5));       // parent of doc-element
        assertEquals(5, doc.getParentNodeFor(6));       // parent of doc-element/text()[1]
        assertEquals(5, doc.getParentNodeFor(7));       // parent of <!-- comment before e1 -->
        assertEquals(5, doc.getParentNodeFor(8));       // parent of doc-element/text()[2]
        assertEquals(5, doc.getParentNodeFor(9));       // parent of e1
        assertEquals(9, doc.getParentNodeFor(10));      // parent of e1/text()[1]
        assertEquals(9, doc.getParentNodeFor(11));      // parent of <?pi-before-e1_1?>
        assertEquals(9, doc.getParentNodeFor(12));      // parent of e1/text()[2]
        assertEquals(9, doc.getParentNodeFor(13));      // parent of e1_1
        assertEquals(13, doc.getParentNodeFor(14));     // parent of e1_1/text()[1]
        assertEquals(9, doc.getParentNodeFor(15));      // parent of e1/text()[3]
        assertEquals(9, doc.getParentNodeFor(16));      // parent of e1_2
        assertEquals(16, doc.getParentNodeFor(17));     // parent of e1_2/text()[1]
        assertEquals(9, doc.getParentNodeFor(18));      // parent of e1/text()[4]
        assertEquals(5, doc.getParentNodeFor(19));      // parent of doc-element/text()[3]
        assertEquals(5, doc.getParentNodeFor(20));      // parent of <!-- comment after e1 -->
        assertEquals(5, doc.getParentNodeFor(21));      // parent of doc-element/text()[4]
        assertEquals(0, doc.getParentNodeFor(22));      // parent of <?pi-after-doc-1?>
        assertEquals(0, doc.getParentNodeFor(23));      // parent of <!-- comment after doc 1 -->
        assertEquals(0, doc.getParentNodeFor(24));      // parent of <?pi-after-doc-2?>
        assertEquals(0, doc.getParentNodeFor(25));      // <!-- comment after doc 2 -->
        assertEquals(0, doc.getParentNodeFor(26));      // <?pi-after-doc-3?>
    }

    // TODO(AR) Move DOM like tests below to somewhere more appropriate

    @Test
    public void getNode() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertTrue(doc.getNode(0) instanceof DocumentImpl);                 // the document node
        assertTrue(doc.getNode(1) instanceof CommentImpl);                  // <!-- comment before doc 1 -->
        assertTrue(doc.getNode(2) instanceof ProcessingInstructionImpl);    // <?pi-before-doc-1?>
        assertTrue(doc.getNode(3) instanceof CommentImpl);                  // <!-- comment before doc 2 -->
        assertTrue(doc.getNode(4) instanceof ProcessingInstructionImpl);    // <?pi-before-doc-2?>
        assertTrue(doc.getNode(5) instanceof ElementImpl);                  // doc-element
        assertTrue(doc.getNode(6) instanceof TextImpl);                     // doc-element/text()[1]
        assertTrue(doc.getNode(7) instanceof CommentImpl);                  // <!-- comment before e1 -->
        assertTrue(doc.getNode(8) instanceof TextImpl);                     // doc-element/text()[2]
        assertTrue(doc.getNode(9) instanceof ElementImpl);                  // e1
        assertTrue(doc.getNode(10) instanceof TextImpl);                    // e1/text()[1]
        assertTrue(doc.getNode(11) instanceof ProcessingInstructionImpl);   // <?pi-before-e1_1?>
        assertTrue(doc.getNode(12) instanceof TextImpl);                    // e1/text()[2]
        assertTrue(doc.getNode(13) instanceof ElementImpl);                 // e1_1
        assertTrue(doc.getNode(14) instanceof TextImpl);                    // e1_1/text()[1]
        assertTrue(doc.getNode(15) instanceof TextImpl);                    // e1/text()[3]
        assertTrue(doc.getNode(16) instanceof ElementImpl);                 // e1_2
        assertTrue(doc.getNode(17) instanceof TextImpl);                    // e1_2/text()[1]
        assertTrue(doc.getNode(18) instanceof TextImpl);                    // e1/text()[4]
        assertTrue(doc.getNode(19) instanceof TextImpl);                    // doc-element/text()[3]
        assertTrue(doc.getNode(20) instanceof CommentImpl);                 // <!-- comment after e1 -->
        assertTrue(doc.getNode(21) instanceof TextImpl);                    // doc-element/text()[4]
        assertTrue(doc.getNode(22) instanceof ProcessingInstructionImpl);   // <?pi-after-doc-1?>
        assertTrue(doc.getNode(23) instanceof CommentImpl);                 // <!-- comment after doc 1 -->
        assertTrue(doc.getNode(24) instanceof ProcessingInstructionImpl);   // <?pi-after-doc-2?>
        assertTrue(doc.getNode(25) instanceof CommentImpl);                 // <!-- comment after doc 2 -->
        assertTrue(doc.getNode(26) instanceof ProcessingInstructionImpl);   // <?pi-after-doc-3?>
    }

    @Test
    public void hasChildNodes() throws IOException, ParserConfigurationException, SAXException {
        final DocumentImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        assertTrue(doc.hasChildNodes());
    }

    @Test
    public void getChildNodes() throws IOException, ParserConfigurationException, SAXException {
        final NodeImpl doc;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(XML.getBytes(UTF_8))) {
            doc = parse(is);
        }

        // children of the document node
        final NodeList docChildren = doc.getChildNodes();
        assertEquals(10, docChildren.getLength());

        // children of <!-- comment before doc 1 -->
        final Node commentBeforeDoc1 = docChildren.item(0);
        assertTrue(commentBeforeDoc1 instanceof CommentImpl);
        assertEquals(0, commentBeforeDoc1.getChildNodes().getLength());

        // children of <?pi-before-doc-1?>
        final Node piBeforeDoc1 = docChildren.item(1);
        assertTrue(piBeforeDoc1 instanceof ProcessingInstructionImpl);
        assertEquals(0, piBeforeDoc1.getChildNodes().getLength());

        // children of <!-- comment before doc 2 -->
        final Node commentBeforeDoc2 = docChildren.item(2);
        assertTrue(commentBeforeDoc2 instanceof CommentImpl);
        assertEquals(0, commentBeforeDoc2.getChildNodes().getLength());

        // children of <?pi-before-doc-2?>
        final Node piBeforeDoc2 = docChildren.item(3);
        assertTrue(piBeforeDoc2 instanceof ProcessingInstructionImpl);
        assertEquals(0, piBeforeDoc2.getChildNodes().getLength());

        // children of doc-element
        final Node docElement = docChildren.item(4);
        assertTrue(docElement instanceof ElementImpl);
        assertEquals("doc-element", docElement.getLocalName());
        final NodeList docElementChildren = docElement.getChildNodes();
        assertEquals(7, docElementChildren.getLength());

        // children of doc-element/text()[1]
        final Node docElementText1 = docElementChildren.item(0);
        assertTrue(docElementText1 instanceof TextImpl);
        assertEquals(0, docElementText1.getChildNodes().getLength());

        // children of <!-- comment before e1 -->
        final Node commentBeforeE1 = docElementChildren.item(1);
        assertTrue(commentBeforeE1 instanceof CommentImpl);
        assertEquals(0, commentBeforeE1.getChildNodes().getLength());

        // children of doc-element/text()[2]
        final Node docElementText2 = docElementChildren.item(2);
        assertTrue(docElementText2 instanceof TextImpl);
        assertEquals(0, docElementText2.getChildNodes().getLength());

        // children of e1
        final Node e1 = docElementChildren.item(3);
        assertTrue(e1 instanceof ElementImpl);
        assertEquals("e1", e1.getLocalName());
        final NodeList e1Children = e1.getChildNodes();
        assertEquals(7, e1Children.getLength());

        // children of e1/text()[1]
        final Node e1Text1 = e1Children.item(0);
        assertTrue(e1Text1 instanceof TextImpl);
        assertEquals(0, e1Text1.getChildNodes().getLength());

        // children of <?pi-before-e1_1?>
        final Node piBeforeE11 = e1Children.item(1);
        assertTrue(piBeforeE11 instanceof ProcessingInstructionImpl);
        assertEquals(0, piBeforeE11.getChildNodes().getLength());

        // children of e1/text()[2]
        final Node e1Text2 = e1Children.item(2);
        assertTrue(e1Text2 instanceof TextImpl);
        assertEquals(0, e1Text2.getChildNodes().getLength());

        // children of e1_1
        final Node e11 = e1Children.item(3);
        assertTrue(e11 instanceof ElementImpl);
        assertEquals("e1_1", e11.getLocalName());
        final NodeList e11Children = e11.getChildNodes();
        assertEquals(1, e11Children.getLength());

        // children of e1_1/text()[1]
        final Node e11Text1 = e11Children.item(0);
        assertTrue(e11Text1 instanceof TextImpl);
        assertEquals(0, e11Text1.getChildNodes().getLength());

        // children of e1/text()[2]
        final Node e1Text3 = e1Children.item(4);
        assertTrue(e1Text3 instanceof TextImpl);
        assertEquals(0, e1Text3.getChildNodes().getLength());

        // children of e1_2
        final Node e12 = e1Children.item(5);
        assertTrue(e12 instanceof ElementImpl);
        assertEquals("e1_2", e12.getLocalName());
        final NodeList e12Children = e12.getChildNodes();
        assertEquals(1, e12Children.getLength());

        // children of e1_2/text()[1]
        final Node e12Text1 = e12Children.item(0);
        assertTrue(e12Text1 instanceof TextImpl);
        assertEquals(0, e12Text1.getChildNodes().getLength());

        // children of e1/text()[4]
        final Node e1Text4 = e1Children.item(6);
        assertTrue(e1Text4 instanceof TextImpl);
        assertEquals(0, e1Text4.getChildNodes().getLength());

        // children of doc-element/text()[3]
        final Node docElementText3 = docElementChildren.item(4);
        assertTrue(docElementText3 instanceof TextImpl);
        assertEquals(0, docElementText3.getChildNodes().getLength());

        // children of <!-- comment after e1 -->
        final Node commentAfterE1 = docElementChildren.item(5);
        assertTrue(commentAfterE1 instanceof CommentImpl);
        assertEquals(0, commentAfterE1.getChildNodes().getLength());

        // children of doc-element/text()[4]
        final Node docElementText4 = docElementChildren.item(6);
        assertTrue(docElementText4 instanceof TextImpl);
        assertEquals(0, docElementText4.getChildNodes().getLength());

        // children of <?pi-after-doc-1?>
        final Node piAfterDoc1 = docChildren.item(5);
        assertTrue(piAfterDoc1 instanceof ProcessingInstructionImpl);
        assertEquals(0, piAfterDoc1.getChildNodes().getLength());

        // children of <!-- comment after doc 1 -->
        final Node commentAfterDoc1 = docChildren.item(6);
        assertTrue(commentAfterDoc1 instanceof CommentImpl);
        assertEquals(0, commentAfterDoc1.getChildNodes().getLength());

        // children of <?pi-after-doc-2?>
        final Node piAfterDoc2 = docChildren.item(7);
        assertTrue(piAfterDoc2 instanceof ProcessingInstructionImpl);
        assertEquals(0, piAfterDoc2.getChildNodes().getLength());

        // children of <!-- comment after doc 2 -->
        final Node commentAfterDoc2 = docChildren.item(8);
        assertTrue(commentAfterDoc2 instanceof CommentImpl);
        assertEquals(0, commentAfterDoc2.getChildNodes().getLength());

        // children of <?pi-after-doc-2?>
        final Node piAfterDoc3 = docChildren.item(9);
        assertTrue(piAfterDoc3 instanceof ProcessingInstructionImpl);
        assertEquals(0, piAfterDoc3.getChildNodes().getLength());
    }

    private DocumentImpl parse(final InputStream is) throws ParserConfigurationException, SAXException, IOException {
        final SAXParserFactory saxParserFactory = ExistSAXParserFactory.getSAXParserFactory();
        final SAXParser saxParser  = saxParserFactory.newSAXParser();
        final XMLReader reader = saxParser.getXMLReader();
        final InputSource src = new InputSource(is);
        final SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);

        reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
        reader.parse(src);
        return adapter.getDocument();
    }
}
