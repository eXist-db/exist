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

package org.exist.xquery.functions.fn;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.ExistSAXParserFactory;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
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
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static SAXParserFactory saxParserFactory = ExistSAXParserFactory.getSAXParserFactory();
    static {
        saxParserFactory.setNamespaceAware(true);
    }

    @Test
    public void doc_dynamicallyAvailableCollection_absoluteUri() throws XPathException, EXistException, PermissionDeniedException {
        final BrokerPool pool = BrokerPool.getInstance();

        final String doc = "<timestamp>" + System.currentTimeMillis() + "</timestamp>";
        final String collectionUri = "http://from-dynamic-context/collection1";
        final String query = "fn:collection('" + collectionUri + "')";

        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext context = new XQueryContext(pool);
            context.addDynamicallyAvailableCollection(collectionUri, (broker2, transaction, uri) -> asInMemoryDocument(doc));

            final XQuery xqueryService = pool.getXQueryService();
            final CompiledXQuery compiled = xqueryService.compile(broker, context, query);
            final Sequence result = xqueryService.execute(broker, compiled, null);

            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0) instanceof Node);

            final Source expectedSource = Input.fromString(doc).build();
            final Source actualSource = Input.fromNode((Node)result.itemAt(0)).build();
            final Diff diff = DiffBuilder.compare(expectedSource)
                    .withTest(actualSource)
                    .checkForIdentical()
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }

    @Test
    public void doc_dynamicallyAvailableCollection_relativeUri() throws XPathException, EXistException, PermissionDeniedException, URISyntaxException {
        final BrokerPool pool = BrokerPool.getInstance();

        final String doc = "<timestamp>" + System.currentTimeMillis() + "</timestamp>";
        final String baseUri = "http://from-dynamic-context/";
        final String collectionRelativeUri = "collection1";
        final String query = "fn:collection('" + collectionRelativeUri + "')";

        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext context = new XQueryContext(pool);
            context.setBaseURI(new AnyURIValue(new URI(baseUri)));
            context.addDynamicallyAvailableCollection(baseUri + collectionRelativeUri, (broker2, transaction, uri) -> asInMemoryDocument(doc));

            final XQuery xqueryService = pool.getXQueryService();
            final CompiledXQuery compiled = xqueryService.compile(broker, context, query);
            final Sequence result = xqueryService.execute(broker, compiled, null);

            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0) instanceof Node);

            final Source expectedSource = Input.fromString(doc).build();
            final Source actualSource = Input.fromNode((Node)result.itemAt(0)).build();
            final Diff diff = DiffBuilder.compare(expectedSource)
                    .withTest(actualSource)
                    .checkForIdentical()
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }

    private DocumentImpl asInMemoryDocument(final String doc) throws XPathException {
        try {
            final SAXAdapter saxAdapter = new SAXAdapter();
            final SAXParser saxParser = saxParserFactory.newSAXParser();
            final XMLReader xmlReader = saxParser.getXMLReader();

            xmlReader.setContentHandler(saxAdapter);
            xmlReader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, saxAdapter);

            try (final Reader reader = new StringReader(doc)) {
                xmlReader.parse(new InputSource(reader));
            }
            return saxAdapter.getDocument();
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new XPathException("Unable to parse document", e);
        }
    }
}
