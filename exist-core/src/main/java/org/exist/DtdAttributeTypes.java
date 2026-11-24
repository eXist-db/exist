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
package org.exist;

import org.exist.dom.persistent.AttrImpl;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The attribute types of {@code ID}, {@code IDREF} and {@code IDREFS} that the internal subset of a DOCTYPE declares.
 *
 * <p>A stored attribute keeps its type, but the declaration that gave it is not stored, so a document that is parsed
 * again, after an export or a restore, has no way to give the type back. These declarations are what is kept with the
 * document type so that it can be written out again as the internal subset. Only the three types are kept, as they
 * are the only ones an attribute node stores. A default is not kept: the attribute values that a default gave are
 * part of the stored document.</p>
 *
 * <p>The first declaration of an attribute wins, as it does in XML. The internal subset is read before the external
 * one, so the declarations here take precedence over those of an external DTD.</p>
 */
final class DtdAttributeTypes {

    /** The result of {@link #typeOf(String, String)} for an attribute the internal subset does not declare. */
    static final int NOT_DECLARED = -1;

    private static final String ID = "ID";
    private static final String IDREF = "IDREF";
    private static final String IDREFS = "IDREFS";

    /** The declarations in the order they were made, by element and attribute name as written. */
    private final Map<String, Declaration> declarations = new LinkedHashMap<>();

    private record Declaration(String element, String attribute, String type, int attrType) {
    }

    /**
     * Records the declaration of an attribute, if it is one of the three types.
     *
     * @param element the name of the element, as written in the declaration
     * @param attribute the name of the attribute, as written in the declaration
     * @param type the type as the parser reports it: {@code ID}, {@code IDREF}, {@code IDREFS}, or any other
     */
    void declare(final String element, final String attribute, final String type) {
        final int attrType = attrType(type);
        if (attrType == NOT_DECLARED) {
            return;
        }
        declarations.putIfAbsent(element + ' ' + attribute, new Declaration(element, attribute, type, attrType));
    }

    /**
     * The {@link AttrImpl} type that the internal subset declares for an attribute.
     *
     * @param element the name of the element, as it is written in the document
     * @param attribute the name of the attribute, as it is written in the document
     * @return {@link AttrImpl#ID}, {@link AttrImpl#IDREF} or {@link AttrImpl#IDREFS}, or {@link #NOT_DECLARED}
     */
    int typeOf(final String element, final String attribute) {
        if (declarations.isEmpty()) {
            return NOT_DECLARED;
        }
        final Declaration declaration = declarations.get(element + ' ' + attribute);
        return declaration == null ? NOT_DECLARED : declaration.attrType;
    }

    boolean isEmpty() {
        return declarations.isEmpty();
    }

    /**
     * The declarations as the internal subset of a DOCTYPE, to be written between {@code [} and {@code ]}.
     * An attribute is always {@code #IMPLIED}: the stored document has the attribute values, and a restore must not
     * fail where an element does not have an attribute that was declared as required.
     *
     * @return the declarations, or null if there are none
     */
    @Nullable
    String toInternalSubset() {
        if (declarations.isEmpty()) {
            return null;
        }
        final StringBuilder subset = new StringBuilder();
        for (final Declaration declaration : declarations.values()) {
            subset.append("<!ATTLIST ").append(declaration.element).append(' ').append(declaration.attribute)
                    .append(' ').append(declaration.type).append(" #IMPLIED>");
        }
        return subset.toString();
    }

    private static int attrType(final String type) {
        return switch (type) {
            case ID -> AttrImpl.ID;
            case IDREF -> AttrImpl.IDREF;
            case IDREFS -> AttrImpl.IDREFS;
            default -> NOT_DECLARED;
        };
    }
}
