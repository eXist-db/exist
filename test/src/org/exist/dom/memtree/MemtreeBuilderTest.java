/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.dom.memtree;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.exist.Namespaces;
import org.exist.util.ExistSAXParserFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;

@RunWith(ParallelParameterized.class)
public class MemtreeBuilderTest {

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "namespaceAware", true },
                { "namespaceIgnorant", false }
        });
    }

    @Parameter
    public String parameterizedTestsName;

    @Parameter(value = 1)
    public boolean namespaceAware;

    @Test
    public void parseSimple() throws IOException, SAXException, ParserConfigurationException {
        final String doc = "<timestamp>" + System.currentTimeMillis() + "</timestamp>";
        final DocumentImpl parsedDoc = parse(doc);

        final Source expectedSource = Input.fromString(doc).build();
        final Source actualSource = Input.fromNode(parsedDoc).build();
        final Diff diff = DiffBuilder.compare(expectedSource)
                .withTest(actualSource)
                .checkForIdentical()
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    private DocumentImpl parse(final String xml) throws ParserConfigurationException, SAXException, IOException {
        final SAXParserFactory saxParserFactory = ExistSAXParserFactory.getSAXParserFactory();
        saxParserFactory.setNamespaceAware(namespaceAware);

        final SAXAdapter saxAdapter = new SAXAdapter();
        final SAXParser saxParser = saxParserFactory.newSAXParser();
        final XMLReader xmlReader = saxParser.getXMLReader();

        xmlReader.setContentHandler(saxAdapter);
        xmlReader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, saxAdapter);

        try (final Reader reader = new StringReader(xml)) {
            xmlReader.parse(new InputSource(reader));
        }

        return saxAdapter.getDocument();
    }
}
