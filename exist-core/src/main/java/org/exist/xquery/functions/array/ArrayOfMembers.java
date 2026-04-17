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
package org.exist.xquery.functions.array;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * array:of-members($input as map(xs:string, item()*)*) — Construct array from member maps.
 * Inverse of array:members.
 */
public class ArrayOfMembers extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("of-members", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Constructs an array from a sequence of member maps (each with a 'value' key).",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.MAP_ITEM, Cardinality.ZERO_OR_MORE, "The member maps")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The resulting array"))
    };

    public ArrayOfMembers(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final List<Sequence> members = new ArrayList<>();
        for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            final AbstractMapType map = (AbstractMapType) i.nextItem();
            final Sequence value = map.get(new StringValue("value"));
            members.add(value != null ? value : Sequence.EMPTY_SEQUENCE);
        }
        return new ArrayType(context, members);
    }
}
