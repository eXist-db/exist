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

import org.apache.xerces.impl.dtd.XMLDTDDescription;
import org.apache.xerces.impl.dtd.XMLDTDLoader;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarLoader;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GrammarPoolTest {

    private final String TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="https://test.exist-db.org/TEMPLATE"
                       xmlns:tst="https://test.exist-db.org"
                       elementFormDefault="qualified">
            
                <!-- Root element -->
                <xs:element name="test">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="id" type="xs:string"/>
                            <xs:element name="value" type="xs:integer"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            
            </xs:schema>
            
            """;

    private GrammarPool grammarPool;

    @BeforeEach
    void prepare() {
        grammarPool = new GrammarPool(10, 10);
    }

    Grammar constructFromText(final String systemId, final String content) {
        try (final InputStream resourceAsStream = new ByteArrayInputStream(content.getBytes())) {
            assertNotNull(resourceAsStream);
            return loadGrammar(systemId, resourceAsStream);

        } catch (final IOException ex) {
            fail(ex.getMessage());
        }
        return null;
    }

    private Grammar loadGrammar(final String systemId, final InputStream resourceAsStream) throws IOException {
        final XMLInputSource inputSource = new XMLInputSource(null, systemId, null, resourceAsStream, null);
        final XMLGrammarLoader schemaLoader = new XMLSchemaLoader();
        return schemaLoader.loadGrammar(inputSource);
    }

    @Test
    void simpleCache() {
        final Grammar xsd = constructFromText("systemid", TEMPLATE);

        // check empty
        assertEquals(0, grammarPool.cachedGrammarCount());

        // add one
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{xsd});

        // check one
        assertEquals(1, grammarPool.cachedGrammarCount());

        // add same one again
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{xsd});

        // still one
        assertEquals(1, grammarPool.cachedGrammarCount());

        // make cache empty
        grammarPool.clear();

        // check empty
        assertEquals(0, grammarPool.cachedGrammarCount());
    }

    @Test
    void sameNamespaceDifferentLocationsAreKeptApart() {
        final Grammar first = constructFromText("file:///first.xsd", TEMPLATE);
        final Grammar second = constructFromText("file:///second.xsd", TEMPLATE);

        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{first});
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{second});

        assertEquals(2, grammarPool.cachedGrammarCount());
        assertEquals(0, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);
        assertSame(first, grammarPool.retrieveGrammar(first.getGrammarDescription()));
        assertSame(second, grammarPool.retrieveGrammar(second.getGrammarDescription()));
    }

    @Test
    void onlyRegisteredSchemasAreOfferedForPreloading() {
        final Grammar discovered = constructFromText("file:///discovered.xsd", TEMPLATE);
        final Grammar registered = constructFromText("file:///registered.xsd", TEMPLATE);

        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{discovered});
        grammarPool.preloadGrammars(new Grammar[]{registered});

        final Grammar[] initial = grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA);
        assertEquals(1, initial.length);
        assertSame(registered, initial[0]);
        // both can still be looked up individually
        assertSame(discovered, grammarPool.retrieveGrammar(discovered.getGrammarDescription()));

        grammarPool.clear();
        assertEquals(0, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);
    }

    @Test
    void fillCache() throws InterruptedException {

        // fill cache
        for (int i = 0; i < 200; i++) {
            final String txt = TEMPLATE.replace("TEMPLATE", "" + i);
            final Grammar xsd = constructFromText("systemid", txt);
            grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{xsd});
        }

        // give cache time to work on cleanup
        Thread.sleep(250);

        // only 5 should be cached
        assertEquals(10, grammarPool.cachedGrammarCount());

        // make cache empty
        grammarPool.clear();

        // check empty
        assertEquals(0, grammarPool.cachedGrammarCount());
    }

    private Grammar loadDtd(final String publicId, final String systemId, final String content) throws IOException {
        final XMLInputSource inputSource = new XMLInputSource(publicId, systemId, null, new StringReader(content), null);
        return new XMLDTDLoader().loadGrammar(inputSource);
    }

    /**
     * See <a href="https://github.com/eXist-db/exist/issues/1757">#1757</a>: DTDs have no target
     * namespace, so different DTDs must not share a cache entry.
     */
    @Test
    void differentDtdsAreCachedSeparately() throws IOException {
        final String publicIdA = "-//EXIST-TEST//DTD A//EN";
        final String systemIdA = "file:///dtds/a.dtd";
        final String publicIdB = "-//EXIST-TEST//DTD B//EN";
        final String systemIdB = "file:///dtds/b.dtd";

        final Grammar dtdA = loadDtd(publicIdA, systemIdA, "<!ELEMENT a (#PCDATA)><!ENTITY alpha \"A\">");
        final Grammar dtdB = loadDtd(publicIdB, systemIdB, "<!ELEMENT b (#PCDATA)><!ENTITY beta \"B\">");

        grammarPool.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtdA});
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtdB});

        assertEquals(2, grammarPool.retrieveCachedDtdGrammars().length);
    }

    /**
     * Xerces does not make the entities declared by a cached DTD available to later documents, so the
     * pool never hands a DTD grammar to a parser itself, see {@link ReaderGrammarPool}.
     */
    @Test
    void dtdGrammarsAreNotGivenToParsers() throws IOException {
        final String publicIdA = "-//EXIST-TEST//DTD A//EN";
        final String systemIdA = "file:///dtds/a.dtd";

        final Grammar dtdA = loadDtd(publicIdA, systemIdA, "<!ELEMENT a (#PCDATA)><!ENTITY alpha \"A\">");
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtdA});

        assertEquals(0, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_DTD).length);
        assertNull(grammarPool.retrieveGrammar(new XMLDTDDescription(publicIdA, systemIdA, null, systemIdA, null)));
        assertEquals(1, grammarPool.retrieveCachedDtdGrammars().length);

        // a reader view only does so once DTD reuse is enabled
        final ReaderGrammarPool readerPool = new ReaderGrammarPool(grammarPool);
        assertEquals(0, readerPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_DTD).length);
        readerPool.enableDtdReuse();
        assertEquals(1, readerPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_DTD).length);
    }
}
