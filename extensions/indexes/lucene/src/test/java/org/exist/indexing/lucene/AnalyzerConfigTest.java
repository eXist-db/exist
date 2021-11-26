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
package org.exist.indexing.lucene;

import org.exist.util.StringInputSource;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class AnalyzerConfigTest {

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    static {
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
    }

    @Test
    public void parameterFromCharArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strParam =
                "<param xmlns=\"http://exist-db.org/collection-config/1.0\" name=\"punctuationDictionary\" type=\"char[]\">\n" +
                "    <value>'</value>\n" +
                "    <value>-</value>\n" +
                "    <value>’</value>\n" +
                "</param>";

        final Element elemParam = parse(strParam).getDocumentElement();
        final AnalyzerConfig.KeyTypedValue<?> constructorParameter = AnalyzerConfig.getConstructorParameter(elemParam);

        assertEquals("punctuationDictionary", constructorParameter.getKey());
        assertEquals(char[].class, constructorParameter.getValueClass());
        assertArrayEquals(new char[] {'\'', '-', '’'}, (char[])constructorParameter.getValue());
    }

    @Test(expected = AnalyzerConfig.ParameterException.class)
    public void parameterFromInvalidCharArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strParam =
                "<param xmlns=\"http://exist-db.org/collection-config/1.0\" name=\"punctuationDictionary\" type=\"char[]\">\n" +
                        "    <value>'</value>\n" +
                        "    <value/>\n" +
                        "    <value>’</value>\n" +
                        "</param>";

        final Element elemParam = parse(strParam).getDocumentElement();
        AnalyzerConfig.getConstructorParameter(elemParam);
    }

    @Test
    public void parameterFromStringArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strParam =
                "<param xmlns=\"http://exist-db.org/collection-config/1.0\" name=\"dictionary\" type=\"java.lang.String[]\">\n" +
                "    <value>hello</value>\n" +
                "    <value>hi</value>\n" +
                "    <value/>\n" +
                "    <value>goodbye</value>\n" +
                "</param>";

        final Element elemParam = parse(strParam).getDocumentElement();
        final AnalyzerConfig.KeyTypedValue<?> constructorParameter = AnalyzerConfig.getConstructorParameter(elemParam);

        assertEquals("dictionary", constructorParameter.getKey());
        assertEquals(String[].class, constructorParameter.getValueClass());
        assertArrayEquals(new String[] {"hello", "hi", "", "goodbye"}, (String[])constructorParameter.getValue());
    }

    private Document parse(final String strXml) throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilder documentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        return documentBuilder.parse(new StringInputSource(strXml));
    }
}
