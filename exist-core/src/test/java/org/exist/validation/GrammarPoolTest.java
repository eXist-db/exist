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
        assertEquals(0, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);

        // add one
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{xsd});

        // check one
        assertEquals(1, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);

        // add same one again
        grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{xsd});

        // still one
        assertEquals(1, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);

        // make cache empty
        grammarPool.clear();

        // check empty
        assertEquals(0, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);
    }

    @Test
    void fillCache() throws InterruptedException {

        // fill cache
        for (int i = 0; i < 200; i++) {
            final String txt = TEMPLATE.replace("TEMPLATE", String.valueOf(i));
            final Grammar xsd = constructFromText("systemid", txt);
            grammarPool.cacheGrammars(XMLGrammarDescription.XML_SCHEMA, new Grammar[]{xsd});
        }

        // give cache time to work on cleanup
        Thread.sleep(250);

        // only 5 should be cached
        assertEquals(10, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);

        // make cache empty
        grammarPool.clear();

        // check empty
        assertEquals(0, grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA).length);
    }

}
