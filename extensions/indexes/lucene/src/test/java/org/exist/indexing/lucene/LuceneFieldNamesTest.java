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

import org.exist.dom.QName;
import org.exist.dom.persistent.SymbolTable;
import org.exist.storage.ElementValue;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The field names of the full-text and range indexes, see https://github.com/eXist-db/exist/issues/6989.
 */
class LuceneFieldNamesTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static SymbolTable symbols() {
        return existEmbeddedServer.getBrokerPool().getSymbols();
    }

    @Test
    void elementNameRoundTrips() {
        final QName name = new QName("item", "http://example.org/ns", "", ElementValue.ELEMENT);
        final String field = LuceneFieldNames.encodeQName(name, symbols());
        assertTrue(field.startsWith("qn:"), field);
        final QName decoded = LuceneFieldNames.decodeQName(field, symbols());
        assertEquals(name.getLocalPart(), decoded.getLocalPart());
        assertEquals(name.getNamespaceURI(), decoded.getNamespaceURI());
        assertEquals(ElementValue.ELEMENT, decoded.getNameType());
    }

    @Test
    void attributeNameRoundTrips() {
        final QName name = new QName("k", "", "", ElementValue.ATTRIBUTE);
        final QName decoded = LuceneFieldNames.decodeQName(LuceneFieldNames.encodeQName(name, symbols()), symbols());
        assertEquals("k", decoded.getLocalPart());
        assertEquals("", decoded.getNamespaceURI());
        assertEquals(ElementValue.ATTRIBUTE, decoded.getNameType());
    }

    /** Configured fields, the index's own fields, and encodings of no name are not element names. */
    @ParameterizedTest
    @ValueSource(strings = {"face", "cafe", "f", "a00010000", "id", "docId", "exist:docId", "exist:id", "qn:", "qn:xyz",
            "qn:ce", "qn:10000", "$facets"})
    void otherFieldsAreNotElementNames(final String field) {
        assertNull(LuceneFieldNames.decodeQName(field, symbols()), field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "docId", "nodeId", "address", "_idx", "_boost", "face", "title-embedding"})
    void anyNCNameIsAConfiguredName(final String name) {
        assertTrue(LuceneFieldNames.isConfiguredName(name), name);
        assertFalse(LuceneFieldNames.isInternal(name), name);
        assertDoesNotThrow(() -> LuceneFieldNames.checkConfiguredName(name, "a field"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"exist:docId", "qn:a00010000", "a:b", "1a", "my field", "", "$facets"})
    void namesThatAreNotNCNamesAreRejected(final String name) {
        assertFalse(LuceneFieldNames.isConfiguredName(name), name);
        assertThrows(DatabaseConfigurationException.class, () -> LuceneFieldNames.checkConfiguredName(name, "a field"));
    }

    @Test
    void theIndexOwnFieldsAreInternal() {
        for (final String field : new String[] {LuceneFieldNames.FIELD_DOC_ID, LuceneFieldNames.FIELD_DOC_ID_KEYWORD,
                LuceneFieldNames.FIELD_DOC_URI, LuceneFieldNames.FIELD_NODE_ID, LuceneFieldNames.FIELD_NODE_ID_DV,
                LuceneFieldNames.FIELD_BOOST, LuceneFieldNames.FIELD_INDEX_TYPE, LuceneFieldNames.FIELD_ADDRESS,
                LuceneFieldNames.FIELD_ID}) {
            assertTrue(LuceneFieldNames.isInternal(field), field);
            assertFalse(LuceneFieldNames.isConfiguredName(field), field);
        }
        assertFalse(LuceneFieldNames.isInternal(LuceneFieldNames.encodeQName(new QName("item", "", "", ElementValue.ELEMENT), symbols())));
    }
}
