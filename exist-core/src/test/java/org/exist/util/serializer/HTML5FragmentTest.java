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
package org.exist.util.serializer;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.test.ExistEmbeddedServer;
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.StringWriter;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Tests that HTML5 serialization does not emit DOCTYPE for fragments
 * (non-html root elements).
 */
public class HTML5FragmentTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private String serialize(final String xquery, final String method, final String version)
            throws EXistException, XPathException, SAXException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.empty())) {
            final XQuery xqueryService = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            final Sequence result = xqueryService.execute(broker, xquery, null);

            final Properties props = new Properties();
            props.setProperty(OutputKeys.METHOD, method);
            props.setProperty(OutputKeys.INDENT, "no");
            props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            if (version != null) {
                props.setProperty(OutputKeys.VERSION, version);
            }
            props.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");

            final StringWriter writer = new StringWriter();
            final XQuerySerializer serializer = new XQuerySerializer(broker, props, writer);
            serializer.serialize(result);
            return writer.toString();
        }
    }

    @Test
    public void htmlDocumentGetsDoctype() throws Exception {
        final String result = serialize("<html><body><p>hello</p></body></html>", "html", "5.0");
        assertTrue("HTML document should have DOCTYPE: " + result,
                result.contains("<!DOCTYPE html>"));
    }

    @Test
    public void htmlFragmentNoDoctype() throws Exception {
        final String result = serialize("<p>hello</p>", "html", "5.0");
        assertFalse("HTML fragment should NOT have DOCTYPE: " + result,
                result.contains("<!DOCTYPE"));
        assertTrue("Fragment content should be preserved: " + result,
                result.contains("<p>hello</p>"));
    }

    @Test
    public void htmlFragmentDivNoDoctype() throws Exception {
        final String result = serialize("<div><span>text</span></div>", "html", "5.0");
        assertFalse("HTML div fragment should NOT have DOCTYPE: " + result,
                result.contains("<!DOCTYPE"));
    }

    @Test
    public void htmlFragmentListNoDoctype() throws Exception {
        final String result = serialize("<li>item</li>", "html", "5.0");
        assertFalse("HTML li fragment should NOT have DOCTYPE: " + result,
                result.contains("<!DOCTYPE"));
    }

    @Test
    public void xhtmlDocumentGetsDoctype() throws Exception {
        final String result = serialize(
                "<html xmlns='http://www.w3.org/1999/xhtml'><body><p>hello</p></body></html>",
                "xhtml", "5.0");
        assertTrue("XHTML document should have DOCTYPE: " + result,
                result.contains("<!DOCTYPE html>"));
    }

    @Test
    public void xhtmlFragmentNoDoctype() throws Exception {
        final String result = serialize(
                "<p xmlns='http://www.w3.org/1999/xhtml'>hello</p>",
                "xhtml", "5.0");
        assertFalse("XHTML fragment should NOT have DOCTYPE: " + result,
                result.contains("<!DOCTYPE"));
    }

    @Test
    public void htmlSuppressIndentation() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.empty())) {
            final XQuery xqueryService = pool.getXQueryService();
            final String xquery = "<html><body><ul><li><p>One</p></li></ul></body></html>";
            final Sequence result = xqueryService.execute(broker, xquery, null);

            final Properties props = new Properties();
            props.setProperty(OutputKeys.METHOD, "html");
            props.setProperty(OutputKeys.INDENT, "yes");
            props.setProperty(OutputKeys.VERSION, "5.0");
            props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            props.setProperty("suppress-indentation", "li td");
            props.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");

            final StringWriter writer = new StringWriter();
            final XQuerySerializer serializer = new XQuerySerializer(broker, props, writer);
            serializer.serialize(result);
            final String output = writer.toString();

            // li should NOT have indentation inside it
            assertTrue("li content should not be indented: " + output,
                    output.contains("<li><p>One</p></li>"));
        }
    }

    @Test
    public void htmlSuppressIndentationViaFnSerialize() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.empty())) {
            final XQuery xqueryService = pool.getXQueryService();
            // Use fn:serialize with suppress-indentation — pass QNames, not string
            final String xquery =
                    "serialize(<html><body><ul><li><p>One</p></li></ul></body></html>, " +
                    "map { 'method': 'html', 'indent': true(), 'version': '5.0', " +
                    "'suppress-indentation': (xs:QName('li'), xs:QName('td')) })";
            final Sequence result = xqueryService.execute(broker, xquery, null);
            final String output = result.getStringValue();

            // li should NOT have indentation inside it
            assertTrue("li content should not be indented via fn:serialize: " + output,
                    output.contains("<li><p>One</p></li>"));
        }
    }

    @Test
    public void htmlCdataSectionElementsSuppressed() throws Exception {
        // For HTML method, cdata-section-elements should be IGNORED
        // Text should not be wrapped in CDATA markers
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.empty())) {
            final XQuery xqueryService = pool.getXQueryService();
            final String xquery = "<html><body><p><b>No CDATA</b></p></body></html>";
            final Sequence result = xqueryService.execute(broker, xquery, null);

            final Properties props = new Properties();
            props.setProperty(OutputKeys.METHOD, "html");
            props.setProperty(OutputKeys.INDENT, "no");
            props.setProperty(OutputKeys.VERSION, "5.0");
            props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            props.setProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "b");
            props.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");

            final StringWriter writer = new StringWriter();
            final XQuerySerializer serializer = new XQuerySerializer(broker, props, writer);
            serializer.serialize(result);
            final String output = writer.toString();

            assertFalse("HTML output should not contain CDATA: " + output,
                    output.contains("<![CDATA["));
            assertTrue("Text should be preserved: " + output,
                    output.contains("<b>No CDATA</b>"));
        }
    }

    @Test
    public void htmlScriptAttributeEscaped() throws Exception {
        // In HTML5, attributes on script elements MUST be escaped
        // but text content inside script elements must NOT be escaped
        final String result = serialize("<html><head><script language='Jack&amp;Jill'>go &amp;&amp; run();</script></head><body/></html>",
                "html", "5.0");
        assertTrue("Script attribute & should be escaped: " + result,
                result.contains("language=\"Jack&amp;Jill\""));
        assertTrue("Script body && should NOT be escaped: " + result,
                result.contains("go && run()"));
    }

    @Test
    public void html40NoDoctypeWithoutPublicSystem() throws Exception {
        // HTML 4.0 without doctype-public/doctype-system should not emit DOCTYPE
        final String result = serialize("<html><body><p>hello</p></body></html>", "html", "4.0");
        assertFalse("HTML 4.0 without public/system should NOT have DOCTYPE: " + result,
                result.contains("<!DOCTYPE"));
    }
}
