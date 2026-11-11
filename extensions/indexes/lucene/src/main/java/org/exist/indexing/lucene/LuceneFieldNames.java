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
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLNames;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;

/**
 * The Lucene field names of the full-text and range indexes. A field a collection configuration names is an
 * {@code xs:NCName}, which has no colon; every name the indexes add themselves has one, in the form
 * {@code kind:payload}. So a configured field can never take the name of one of the index's own fields, and the
 * names of elements and attributes can be told from configured fields by their prefix alone.
 */
public final class LuceneFieldNames {

    private static final String INTERNAL_PREFIX = "exist:";

    public static final String FIELD_NODE_ID = INTERNAL_PREFIX + "nodeId";
    /** DocValues-only field for nodeId; avoids conflict with indexed Field of same name (LUCENE-6019). */
    public static final String FIELD_NODE_ID_DV = INTERNAL_PREFIX + "nodeIdValues";
    public static final String FIELD_DOC_ID = INTERNAL_PREFIX + "docId";
    public static final String FIELD_DOC_ID_KEYWORD = INTERNAL_PREFIX + "docIdKeyword";
    public static final String FIELD_DOC_URI = INTERNAL_PREFIX + "docUri";
    /** Per-document boost for attribute/config-based scoring (Lucene 10: index-time boost via FloatDocValues) */
    public static final String FIELD_BOOST = INTERNAL_PREFIX + "boost";
    /** Identifies which index config created the Lucene doc; used to filter when multiple indexes share a collection. */
    public static final String FIELD_INDEX_TYPE = INTERNAL_PREFIX + "indexType";
    /** The range index's storage address of a node. */
    public static final String FIELD_ADDRESS = INTERNAL_PREFIX + "address";
    /** The range index's id of a node, document id and node id, by which its entries are removed. */
    public static final String FIELD_ID = INTERNAL_PREFIX + "id";

    private static final String QNAME_PREFIX = "qn:";

    private LuceneFieldNames() {
    }

    /**
     * Whether a name can be a field a collection configuration names, as opposed to one of the index's own.
     *
     * @param name the field name
     * @return true if the name is an NCName
     */
    public static boolean isConfiguredName(final String name) {
        return XMLNames.isNCName(name);
    }

    /**
     * Whether a field is one of the index's own, such as {@link #FIELD_DOC_ID}, rather than a configured field or the
     * entries of an element or attribute.
     *
     * @param name the field name
     * @return true for the index's own fields
     */
    public static boolean isInternal(final String name) {
        return name.startsWith(INTERNAL_PREFIX);
    }

    /**
     * Checks the name a collection configuration gives a field.
     *
     * @param name the name
     * @param element the configuration element, for the error message
     * @throws DatabaseConfigurationException if the name is not an NCName
     */
    public static void checkConfiguredName(final String name, final String element) throws DatabaseConfigurationException {
        if (!isConfiguredName(name)) {
            throw new DatabaseConfigurationException("Invalid config: the name of " + element + " must be an NCName, without a colon: '" + name + "'");
        }
    }

    /**
     * The field name of an element's or attribute's entries: its namespace and local name by their ids in the symbol
     * table, as hexadecimal, after the prefix {@code qn:}.
     *
     * @param qname the name
     * @param symbols the symbol table
     * @return the field name
     */
    public static String encodeQName(final QName qname, final SymbolTable symbols) {
        final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
        final short localNameId = symbols.getSymbol(qname.getLocalPart());
        final long nameId = qname.getNameType() | (namespaceId & 0xFFFF) << 16 | (localNameId & 0xFFFFFFFFL) << 32;
        return QNAME_PREFIX + Long.toHexString(nameId);
    }

    /**
     * The element or attribute name a field holds the entries of.
     *
     * @param field the field name
     * @param symbols the symbol table
     * @return the name, or null if the field is not an element's or attribute's
     */
    public static @Nullable QName decodeQName(final String field, final SymbolTable symbols) {
        if (!field.startsWith(QNAME_PREFIX)) {
            return null;
        }
        final long l;
        try {
            // encodeQName writes the long as unsigned
            l = Long.parseUnsignedLong(field, QNAME_PREFIX.length(), field.length(), 16);
        } catch (final NumberFormatException e) {
            return null;
        }
        final byte type = (byte) (l & 0xFFL);
        if (type != ElementValue.ELEMENT && type != ElementValue.ATTRIBUTE) {
            return null;
        }
        final String localName = symbols.getName((short) ((l >>> 32) & 0xFFFFL));
        if (localName == null || localName.isEmpty()) {
            return null;
        }
        final String namespaceURI = symbols.getNamespace((short) ((l >>> 16) & 0xFFFFL));
        return new QName(localName, namespaceURI, XMLConstants.DEFAULT_NS_PREFIX, type);
    }
}
