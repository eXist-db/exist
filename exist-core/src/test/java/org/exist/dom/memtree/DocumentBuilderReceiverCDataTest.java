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

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A CDATA section parsed into the in-memory DOM must survive as a CDATA node, not decay into a
 * text node — see <a href="https://github.com/eXist-db/exist/issues/2081">issue #2081</a>.
 *
 * <p>{@link DocumentBuilderReceiver} is the {@code LexicalHandler} used when
 * {@code request:get-data()} parses an XML request body. Its {@code startCDATA}/{@code endCDATA}
 * callbacks were no-ops, so a SAX parser's CDATA content arrived through {@code characters()} and
 * was built as plain text — and was then re-serialized escaped, turning {@code >} into
 * {@code &gt;} inside {@code <script>} and {@code <style>} blocks on save.</p>
 *
 * <p>{@link SAXAdapter} (used by {@code fn:parse-xml}) already buffered between the two callbacks;
 * these tests pin that both adapters into {@link MemTreeBuilder} now agree.</p>
 */
public class DocumentBuilderReceiverCDataTest {

    private static final String CDATA_DOC =
            "<doc><script><![CDATA[  if (a > b) {} ]]></script></doc>";

    /** Parses through the receiver under test and returns the in-memory document. */
    private static Document parseVia(final DocumentBuilderReceiver receiver, final String xml)
            throws SAXException, IOException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader;
        try {
            reader = factory.newSAXParser().getXMLReader();
        } catch (final Exception e) {
            throw new SAXException(e);
        }
        reader.setContentHandler(receiver);
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", receiver);
        reader.parse(new InputSource(new StringReader(xml)));
        return receiver.getDocument();
    }

    private static Node onlyChildOfScript(final Document doc) {
        final NodeList scripts = doc.getDocumentElement().getElementsByTagName("script");
        assertEquals("expected exactly one <script>", 1, scripts.getLength());
        final NodeList children = scripts.item(0).getChildNodes();
        assertEquals("expected exactly one child node inside <script>", 1, children.getLength());
        return children.item(0);
    }

    /** The regression: the section must be a CDATA node, not a text node. */
    @Test
    public void cdataSectionSurvivesAsACDataNode() throws SAXException, IOException {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        final Document doc = parseVia(new DocumentBuilderReceiver(builder), CDATA_DOC);

        assertNotNull(doc);
        final Node child = onlyChildOfScript(doc);
        assertEquals("a CDATA section must not decay into a text node",
                Node.CDATA_SECTION_NODE, child.getNodeType());
        assertEquals("  if (a > b) {} ", child.getNodeValue());
    }

    /** Parses through SAXAdapter, the sibling adapter into the same builder. */
    private static Node viaSaxAdapter(final String xml) throws SAXException, IOException {
        final SAXAdapter adapter = new SAXAdapter();
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader;
        try {
            reader = factory.newSAXParser().getXMLReader();
        } catch (final Exception e) {
            throw new SAXException(e);
        }
        reader.setContentHandler(adapter);
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
        reader.parse(new InputSource(new StringReader(xml)));
        return onlyChildOfScript(adapter.getDocument());
    }

    /**
     * The two adapters into {@link MemTreeBuilder} must build the same thing from the same bytes.
     *
     * <p>{@link SAXAdapter} backs {@code fn:parse-xml} and always handled CDATA;
     * {@link DocumentBuilderReceiver} backs {@code request:get-data()} and did not. That
     * disagreement is the whole of issue #2081, so this comparison is the regression test —
     * it fails against the unfixed receiver even though SAXAdapter alone would pass.</p>
     */
    @Test
    public void bothAdaptersBuildTheSameNodeFromTheSameBytes() throws SAXException, IOException {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        final Node viaReceiver = onlyChildOfScript(
                parseVia(new DocumentBuilderReceiver(builder), CDATA_DOC));
        final Node viaAdapter = viaSaxAdapter(CDATA_DOC);

        assertEquals("fn:parse-xml and request:get-data() must agree on the node type",
                viaAdapter.getNodeType(), viaReceiver.getNodeType());
        assertEquals(viaAdapter.getNodeValue(), viaReceiver.getNodeValue());
        assertEquals(Node.CDATA_SECTION_NODE, viaReceiver.getNodeType());
    }

    /** Ordinary text must still build as a text node. */
    @Test
    public void ordinaryTextIsStillATextNode() throws SAXException, IOException {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        final Document doc = parseVia(new DocumentBuilderReceiver(builder), "<doc><script>plain</script></doc>");

        final Node child = onlyChildOfScript(doc);
        assertEquals(Node.TEXT_NODE, child.getNodeType());
        assertEquals("plain", child.getNodeValue());
    }

    /**
     * Text on both sides of a CDATA section must not be swallowed by the buffer.
     *
     * <p>Note what this does <em>not</em> assert. {@link MemTreeBuilder#cdataSection(CharSequence)}
     * deliberately appends to a preceding text node ("XML does not allow adjacent text nodes"), so a
     * section sandwiched between two text runs coalesces into one text node and its CDATA-ness is
     * lost. That is pre-existing builder behavior, not something this fix introduces — {@link
     * SAXAdapter} calls the same method and behaves identically, which is what the parity assertion
     * below pins. Issue #2081's case (an element whose entire content is one CDATA section) is
     * unaffected, because there is no preceding text node to coalesce with.</p>
     */
    @Test
    public void textAroundACDataSectionIsPreservedAndBothAdaptersAgree() throws SAXException, IOException {
        final String mixed = "<doc><script>before<![CDATA[ a > b ]]>after</script></doc>";

        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        final Node viaReceiver = onlyChildOfScript(
                parseVia(new DocumentBuilderReceiver(builder), mixed));

        final Node viaAdapter = viaSaxAdapter(mixed);

        // the content is what matters, and no character may be dropped by the CDATA buffer
        assertEquals("before a > b after", viaReceiver.getNodeValue());
        assertEquals("before a > b after", viaAdapter.getNodeValue());
        // and both routes into MemTreeBuilder must agree on the resulting node type
        assertEquals("the two adapters must build the same structure",
                viaAdapter.getNodeType(), viaReceiver.getNodeType());
    }
}
