/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist-dh Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.backup;

import net.jcip.annotations.NotThreadSafe;
import org.exist.Namespaces;
import org.exist.util.ExistSAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class DescriptorResourceCounter {

    private final static SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
    static {
        saxFactory.setNamespaceAware(true);
    }

    private final XMLReader xmlReader;
    private final CounterHandler counterHandler;

    public DescriptorResourceCounter() throws ParserConfigurationException, SAXException {
        final SAXParser saxParser = saxFactory.newSAXParser();
        this.xmlReader = saxParser.getXMLReader();
        this.counterHandler = new CounterHandler();

        xmlReader.setContentHandler(counterHandler);
    }

    public long count(final InputStream descriptorInputStream) throws IOException, SAXException {
        xmlReader.parse(new InputSource(descriptorInputStream));
        final long numberOfFiles = counterHandler.numberOfFiles;

        // reset
        counterHandler.numberOfFiles = 0;
        return numberOfFiles;
    }

    private static class CounterHandler extends DefaultHandler {
        long numberOfFiles = 0;

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
            if (Namespaces.EXIST_NS.equals(uri) && "resource".equals(localName)) {
                numberOfFiles++;
            }
        }
    }
}
