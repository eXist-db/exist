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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implements XQuery 4.0 fn:type-of.
 *
 * Returns a string representation of the type of the supplied value,
 * matching the SequenceType grammar.
 */
public class FnTypeOf extends BasicFunction {

    public static final FunctionSignature FN_TYPE_OF = new FunctionSignature(
            new QName("type-of", Function.BUILTIN_FUNCTION_NS),
            "Returns a string describing the type of the supplied value.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value to inspect")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "string matching SequenceType grammar"));

    public FnTypeOf(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence value = args[0];

        if (value.isEmpty()) {
            return new StringValue(this, "empty-sequence()");
        }

        // Collect distinct type strings, preserving order
        final Set<String> typeStrings = new LinkedHashSet<>();
        for (final SequenceIterator i = value.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            typeStrings.add(itemTypeString(item));
        }

        final StringBuilder sb = new StringBuilder();
        if (typeStrings.size() > 1) {
            sb.append('(');
        }
        boolean first = true;
        for (final String ts : typeStrings) {
            if (!first) {
                sb.append('|');
            }
            sb.append(ts);
            first = false;
        }
        if (typeStrings.size() > 1) {
            sb.append(')');
        }

        // Occurrence indicator
        final int count = value.getItemCount();
        if (count > 1) {
            sb.append('+');
        }

        return new StringValue(this, sb.toString());
    }

    private String itemTypeString(final Item item) {
        final int type = item.getType();

        // Node types
        switch (type) {
            case Type.DOCUMENT:
                return "document-node()";
            case Type.ELEMENT:
                return "element()";
            case Type.ATTRIBUTE:
                return "attribute()";
            case Type.TEXT:
                return "text()";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction()";
            case Type.COMMENT:
                return "comment()";
            case Type.NAMESPACE:
                return "namespace-node()";
        }

        // Function types
        if (Type.subTypeOf(type, Type.ARRAY_ITEM)) {
            return "array(*)";
        }
        if (Type.subTypeOf(type, Type.MAP_ITEM)) {
            return "map(*)";
        }
        if (Type.subTypeOf(type, Type.FUNCTION)) {
            return "fn(*)";
        }

        // Atomic types: getTypeName already includes the xs: prefix
        if (Type.subTypeOf(type, Type.ANY_ATOMIC_TYPE)) {
            final String typeName = Type.getTypeName(type);
            if (typeName != null) {
                return typeName;
            }
            return "xs:anyAtomicType";
        }

        return "item()";
    }
}
