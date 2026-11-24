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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The attribute types that the internal subset of a DOCTYPE declares.
 *
 * <p>A stored attribute keeps its type, but the declaration that gave it is not stored, so a document that is parsed
 * again, after an export or a restore, has no way to give the type back. These declarations are what is kept with the
 * document type so that it can be written out again as the internal subset. Only {@code ID}, {@code IDREF} and
 * {@code IDREFS} are written, as they are the only types an attribute node stores. A default is not kept: the attribute
 * values that a default gave are part of the stored document.</p>
 *
 * <p>The first declaration of an attribute wins, as it does in XML. The internal subset is read before the external
 * one, so the declarations here take precedence over those of an external DTD. That includes a declaration of another
 * type for an attribute that the external DTD declares as an ID: it is kept, as {@code CDATA}, but only when the
 * DOCTYPE names an external DTD, as it has no other purpose.</p>
 */
final class DtdAttributeTypes {

    /** The result of {@link #typeOf(String, String)} for an attribute the internal subset does not declare. */
    static final int NOT_DECLARED = -1;

    private static final String CDATA = "CDATA";
    private static final String ID = "ID";
    private static final String IDREF = "IDREF";
    private static final String IDREFS = "IDREFS";

    /** The declarations by element and attribute name as written, to find the one of an attribute of the document. */
    private final Map<String, Map<String, Declaration>> byElement = new HashMap<>();

    /** The same declarations in the order they were made, for the internal subset to write. */
    private final List<Declaration> inOrder = new ArrayList<>();

    private record Declaration(String element, String attribute, String type, int attrType) {
    }

    /**
     * Records the declaration of an attribute. A type other than the three is recorded as {@code CDATA}.
     *
     * @param element the name of the element, as written in the declaration
     * @param attribute the name of the attribute, as written in the declaration
     * @param type the type as the parser reports it: {@code ID}, {@code IDREF}, {@code IDREFS}, or any other
     */
    void declare(final String element, final String attribute, final String type) {
        final int attrType = attrType(type);
        final Declaration declaration = new Declaration(element, attribute, attrType == AttrImpl.CDATA ? CDATA : type, attrType);
        if (byElement.computeIfAbsent(element, name -> new HashMap<>()).putIfAbsent(attribute, declaration) == null) {
            inOrder.add(declaration);
        }
    }

    /**
     * The {@link AttrImpl} type that the internal subset declares for an attribute.
     *
     * @param element the name of the element, as it is written in the document
     * @param attribute the name of the attribute, as it is written in the document
     * @return {@link AttrImpl#ID}, {@link AttrImpl#IDREF}, {@link AttrImpl#IDREFS} or, for any other declared type,
     *     {@link AttrImpl#CDATA}; or {@link #NOT_DECLARED}
     */
    int typeOf(final String element, final String attribute) {
        if (inOrder.isEmpty()) {
            return NOT_DECLARED;
        }
        final Map<String, Declaration> ofElement = byElement.get(element);
        final Declaration declaration = ofElement == null ? null : ofElement.get(attribute);
        return declaration == null ? NOT_DECLARED : declaration.attrType;
    }

    /**
     * The declarations to keep with the document type, as the internal subset of a DOCTYPE to be written between
     * {@code [} and {@code ]}. An attribute is always {@code #IMPLIED}: the stored document has the attribute values,
     * and a restore must not fail where an element does not have an attribute that was declared as required.
     *
     * @param hasExternalSubset true if the DOCTYPE names an external DTD, whose declarations of an attribute as an ID
     *     are overridden by a declaration of another type here
     * @return the declarations, or null if there are none to keep
     */
    @Nullable
    String toInternalSubset(final boolean hasExternalSubset) {
        final StringBuilder subset = new StringBuilder();
        for (final Declaration declaration : inOrder) {
            if (declaration.attrType != AttrImpl.CDATA || hasExternalSubset) {
                subset.append("<!ATTLIST ").append(declaration.element).append(' ').append(declaration.attribute)
                        .append(' ').append(declaration.type).append(" #IMPLIED>");
            }
        }
        return subset.isEmpty() ? null : subset.toString();
    }

    private static int attrType(final String type) {
        return switch (type) {
            case ID -> AttrImpl.ID;
            case IDREF -> AttrImpl.IDREF;
            case IDREFS -> AttrImpl.IDREFS;
            default -> AttrImpl.CDATA;
        };
    }
}
