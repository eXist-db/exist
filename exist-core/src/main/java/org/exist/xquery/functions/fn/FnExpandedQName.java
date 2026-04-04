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
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Implements fn:expanded-QName (XQuery 4.0).
 *
 * Returns a string in Q{uri}local format for a QName value.
 */
public class FnExpandedQName extends BasicFunction {

    public static final FunctionSignature FN_EXPANDED_QNAME = new FunctionSignature(
            new QName("expanded-QName", Function.BUILTIN_FUNCTION_NS),
            "Returns the expanded QName in Q{uri}local notation.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.QNAME, Cardinality.ZERO_OR_ONE,
                            "The QName value")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                    "the expanded QName string in Q{uri}local format"));

    public FnExpandedQName(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final QNameValue qnameVal = (QNameValue) args[0].itemAt(0);
        final QName qname = qnameVal.getQName();

        final String ns = qname.getNamespaceURI() != null ? qname.getNamespaceURI() : "";
        final String local = qname.getLocalPart();

        return new StringValue(this, "Q{" + ns + "}" + local);
    }
}
