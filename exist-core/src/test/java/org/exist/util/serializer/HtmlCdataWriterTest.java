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

import java.io.StringWriter;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.dom.QName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Retrieving a stored document is not XDM serialization, so a CDATA node is normally written as a
 * CDATA section. The html output method has no CDATA sections for HTML elements though (see
 * <a href="https://www.w3.org/TR/xslt-xquery-serialization-31/#html-output">Serialization 3.1
 * section 7.1</a>), only for XML islands, so there it must write the content as character data.
 *
 * <p>See <a href="https://github.com/eXist-db/exist/issues/6810">issue #6810</a>.</p>
 */
public class HtmlCdataWriterTest {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    private static String write(final XMLWriter writer, final StringWriter target, final String method,
            final QName element, final String cdata) throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(OutputKeys.METHOD, method);
        properties.setProperty("html-version", "5.0");
        properties.setProperty(OutputKeys.INDENT, "no");
        writer.setOutputProperties(properties);

        writer.startElement(element);
        writer.cdataSection(cdata.toCharArray(), 0, cdata.length());
        writer.endElement(element);
        return target.toString();
    }

    private static String html(final String method, final QName element, final String cdata) throws Exception {
        final StringWriter target = new StringWriter();
        return write(new HTML5Writer(target), target, method, element, cdata);
    }

    @Test
    public void htmlMethodWritesScriptContentWithoutCdata() throws Exception {
        assertEquals("<script>if (a < b) f()</script>", html("html", new QName("script"), "if (a < b) f()"));
    }

    @Test
    public void htmlMethodWritesStyleContentWithoutCdata() throws Exception {
        assertEquals("<style>ul > li { color: red }</style>", html("html", new QName("style"), "ul > li { color: red }"));
    }

    @Test
    public void htmlMethodKeepsCdataInXmlIslands() throws Exception {
        assertEquals("<text><![CDATA[a < b]]></text>",
                html("html", new QName("text", SVG_NS), "a < b"));
    }

    @Test
    public void xmlSyntaxMethodsKeepCdata() throws Exception {
        final StringWriter target = new StringWriter();
        assertEquals("<script><![CDATA[if (a < b) f()]]></script>",
                write(new XHTMLWriter(target), target, "xhtml", new QName("script"), "if (a < b) f()"));
    }
}
