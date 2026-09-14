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
package org.exist.xquery.xquf;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * W3C XQuery Update Facility 3.0 - fn:put function.
 *
 * <pre>
 * fn:put($node as node(), $uri as xs:string) as empty-sequence()
 * </pre>
 *
 * Adds a put primitive to the PUL for deferred persistence.
 */
public class XQUFFnPut extends BasicFunction {

    public static final FunctionSignature SIGNATURE = new FunctionSignature(
            new QName("put", Function.BUILTIN_FUNCTION_NS, "fn"),
            "Stores a document node to a specified URI. The actual storage is deferred "
                    + "to the end of the snapshot (pending update list application).",
            new SequenceType[]{
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                            "The node to store"),
                    new FunctionParameterSequenceType("uri", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The URI where the node should be stored")
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    public XQUFFnPut(final XQueryContext context) {
        super(context, SIGNATURE);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final NodeValue node = (NodeValue) args[0].itemAt(0);
        final String uri = args[1].getStringValue();

        final PendingUpdateList pul = context.getPendingUpdateList();
        pul.addPrimitive(UpdatePrimitive.put(node.getNode(), uri, this));

        return Sequence.EMPTY_SEQUENCE;
    }
}
