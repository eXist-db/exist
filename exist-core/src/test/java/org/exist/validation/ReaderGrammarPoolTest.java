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
package org.exist.validation;

import org.apache.xerces.impl.dtd.XMLDTDLoader;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.junit.jupiter.api.Test;
import org.xml.sax.XMLReader;

import com.sun.net.httpserver.HttpServer;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The view of the shared pool that a reader has does not cache the DTD grammars of the documents that are parsed with
 * it: Xerces builds such a grammar together with the internal subset of the document.
 */
public class ReaderGrammarPoolTest {

    @Test
    void theDtdGrammarOfAParseIsNotCached() throws IOException {
        final GrammarPool shared = new GrammarPool();
        final ReaderGrammarPool view = new ReaderGrammarPool(shared);

        final Grammar dtd = new XMLDTDLoader().loadGrammar(new XMLInputSource("-//EXIST-TEST//DTD A//EN", "file:///dtds/a.dtd", null,
                new StringReader("<!ELEMENT a (#PCDATA)><!ENTITY alpha \"A\">"), null));
        view.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtd});

        assertEquals(0, shared.retrieveCachedDtdGrammars().length);

        // what is cached through the shared pool itself is offered to a reader that enables DTD reuse, as before
        shared.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtd});
        view.enableDtdReuse();
        assertEquals(1, view.retrieveInitialGrammarSet(XMLGrammarDescription.XML_DTD).length);
    }

    /**
     * The parse that builds the grammar of a DTD fails when the DTD can not be read. The DTD is then remembered as one
     * that is not asked for, like a DTD that is read and found not reusable, and not requested again for each document.
     */
    @Test
    void aDtdWhoseGrammarCouldNotBeBuiltIsNotAskedForAgain() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        http.start();
        try {
            final String dtd = "http://127.0.0.1:" + http.getAddress().getPort() + "/unreadable.dtd";
            final GrammarPool shared = new GrammarPool();
            final ReaderGrammarPool view = new ReaderGrammarPool(shared);
            final XMLReader reader = readerOf(view);

            view.requestSeed(null, dtd, null, "a");
            view.seed(reader);
            assertEquals(1, requests.get(), "the first document asks for the grammar and the DTD is requested");
            assertTrue(shared.isKnownNotReusableDtd(null, dtd), "the failure is remembered");
            assertEquals(0, shared.retrieveCachedDtdGrammars().length, "and no grammar is cached");

            view.requestSeed(null, dtd, null, "a");
            view.seed(reader);
            assertEquals(1, requests.get(), "the next document does not ask for the grammar again");
        } finally {
            http.stop(0);
        }
    }

    private static XMLReader readerOf(final ReaderGrammarPool view) throws Exception {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true);
        reader.setProperty("http://apache.org/xml/properties/internal/grammar-pool", view);
        return reader;
    }
}
