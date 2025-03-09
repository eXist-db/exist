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

package org.exist.util.serializer.json;

import org.exist.storage.serializers.EXistOutputKeys;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.util.serializer.SAXSerializer;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * Created by aretter on 16/05/2017.
 */
public class JSONWriterTest {

    private static final String EOL = System.lineSeparator();
    private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    static {
        documentBuilderFactory.setIgnoringElementContentWhitespace(false);
    }
    private static final TransformerFactory transformerFactory;
    static {
        transformerFactory = TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", null);
    }

    @Test
    public void whitespaceTextNodes() throws IOException, TransformerException, ParserConfigurationException, SAXException {

        final Node xmlDoc = parseXml(
                "<a z='99'>" + EOL +
                "    <b x='1'/>" + EOL +
                "    <b x='2'></b>" + EOL +
                "    <b x='3'>stuff</b>" + EOL +
                "    <b x='4'>\t\r\n   \r\n</b>" + EOL +
                "</a>");

        final Properties properties = new Properties();
        properties.setProperty(OutputKeys.METHOD, "json");
        properties.setProperty(OutputKeys.INDENT, "no");

        final SAXSerializer serializer = new SAXSerializer();
        try(final StringWriter writer = new StringWriter()) {
            serializer.setOutput(writer, properties);
            final Transformer transformer = transformerFactory.newTransformer();
            final SAXResult saxResult = new SAXResult(serializer);
            transformer.transform(new DOMSource(xmlDoc), saxResult);

            final String result = writer.toString();

            assertEquals("{\"z\":\"99\",\"#text\":[\"\\n    \",\"\\n    \",\"\\n    \",\"\\n    \",\"\\n\"],\"b\":[{\"x\":\"1\"},{\"x\":\"2\"},{\"x\":\"3\",\"#text\":\"stuff\"},{\"x\":\"4\",\"#text\":\"\\t\\n   \\n\"}]}", result);
        }
    }

    @Test
    public void ignoreWhitespaceTextNodes() throws IOException, TransformerException, ParserConfigurationException, SAXException {

        final Node xmlDoc = parseXml(
                "<a z='99'>" + EOL +
                        "    <b x='1'/>" + EOL +
                        "    <b x='2'></b>" + EOL +
                        "    <b x='3'>stuff</b>" + EOL +
                        "    <b x='4'>\t\r\n   \r\n</b>" + EOL +
                        "</a>");

        final Properties properties = new Properties();
        properties.setProperty(OutputKeys.METHOD, "json");
        properties.setProperty(EXistOutputKeys.JSON_IGNORE_WHITESPACE_TEXT_NODES, "yes");
        properties.setProperty(OutputKeys.INDENT, "no");

        final SAXSerializer serializer = new SAXSerializer();
        try(final StringWriter writer = new StringWriter()) {
            serializer.setOutput(writer, properties);
            final Transformer transformer = transformerFactory.newTransformer();
            final SAXResult saxResult = new SAXResult(serializer);
            transformer.transform(new DOMSource(xmlDoc), saxResult);

            final String result = writer.toString();

            assertEquals("{\"z\":\"99\",\"b\":[{\"x\":\"1\"},{\"x\":\"2\"},{\"x\":\"3\",\"#text\":\"stuff\"},{\"x\":\"4\"}]}", result);
        }
    }

    @Test
    public void serializesMixedContent_whenAttrsPresent() throws IOException, TransformerException, ParserConfigurationException, SAXException {
        final Node xmlDoc = parseXml(
                "<a x='y' xx='yy'>" + EOL +
                            "\tbefore-b" + EOL +
                            "\t<b y='z'>before-c <c>c-value</c> after-c</b>" + EOL +
                            "\tafter-b" + EOL +
                        "</a>");

        final Properties properties = new Properties();
        properties.setProperty(OutputKeys.METHOD, "json");
        properties.setProperty(OutputKeys.INDENT, "no");

        final SAXSerializer serializer = new SAXSerializer();
        try(final StringWriter writer = new StringWriter()) {
            serializer.setOutput(writer, properties);
            final Transformer transformer = transformerFactory.newTransformer();
            final SAXResult saxResult = new SAXResult(serializer);
            transformer.transform(new DOMSource(xmlDoc), saxResult);

            final String result = writer.toString();

            assertEquals("{\"x\":\"y\",\"xx\":\"yy\",\"#text\":[\"\\n\\tbefore-b\\n\\t\",\"\\n\\tafter-b\\n\"],\"b\":{\"y\":\"z\",\"#text\":[\"before-c \",\" after-c\"],\"c\":\"c-value\"}}", result);
        }
    }

    @Test
    public void serializesMixedContent() throws IOException, TransformerException, ParserConfigurationException, SAXException {
        final Node xmlDoc = parseXml(
                "<a>" + EOL +
                            "\tbefore-b" + EOL +
                            "\t<b>before-c <c>c-value</c> after-c</b>" + EOL +
                            "\tafter-b" + EOL +
                        "</a>");

        final Properties properties = new Properties();
        properties.setProperty(OutputKeys.METHOD, "json");
        properties.setProperty(OutputKeys.INDENT, "no");

        final SAXSerializer serializer = new SAXSerializer();
        try(final StringWriter writer = new StringWriter()) {
            serializer.setOutput(writer, properties);
            final Transformer transformer = transformerFactory.newTransformer();
            final SAXResult saxResult = new SAXResult(serializer);
            transformer.transform(new DOMSource(xmlDoc), saxResult);

            final String result = writer.toString();

            assertEquals("{\"#text\":[\"\\n\\tbefore-b\\n\\t\",\"\\n\\tafter-b\\n\"],\"b\":{\"#text\":[\"before-c \",\" after-c\"],\"c\":\"c-value\"}}", result);
        }
    }

    private Document parseXml(final String xmlStr) throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        try(final InputStream is = new UnsynchronizedByteArrayInputStream(xmlStr.getBytes(UTF_8))) {
            return documentBuilder.parse(is);
        }
    }
}
